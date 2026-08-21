"""jsonl -> packed arrays for training.

Stores the *raw* resampled (x, y) trajectory rather than derived features,
because the augmentation pipeline has to act on coordinates -- jointly with the
layout keys -- before velocity/acceleration/curvature are computed from them.
Deriving features here would freeze them at the un-augmented geometry and throw
away the entire point of co-augmentation.

Following the paper: resample to 60 Hz, then to T=64 points evenly spaced *in
time* (not arc length). Time spacing is what preserves the dwell-and-hesitate
signal that separates 'putt' from 'put'.
"""
from __future__ import annotations

import os
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np
import orjson

T_IN = 64
RESAMPLE_HZ = 60.0
MIN_POINTS = 4
MIN_LETTERS = 2
MAX_LETTERS = 30
MAX_DISTANCE = 1000.0        # the >=100000 values are skip sentinels
MAX_DURATION_MS = 20000.0


def _letters(word: str) -> bytes:
    return bytes(ord(c) - 97 for c in word.lower() if "a" <= c <= "z")


def _resample(xs, ys, ts):
    """60 Hz pass, then T_IN points evenly spaced in time."""
    dur = ts[-1] - ts[0]
    if dur <= 0 or dur > MAX_DURATION_MS:
        return None
    t0 = ts - ts[0]
    n60 = max(int(dur / 1000.0 * RESAMPLE_HZ) + 1, 2)
    g60 = np.linspace(0.0, dur, n60)
    x60 = np.interp(g60, t0, xs)
    y60 = np.interp(g60, t0, ys)
    g = np.linspace(0.0, dur, T_IN)
    return (np.interp(g, g60, x60).astype(np.float32),
            np.interp(g, g60, y60).astype(np.float32),
            np.float32(dur))


def _worker(args):
    path, start, end = args
    traces, labels, lengths, words, durs, sessions = [], [], [], [], [], []
    with open(path, "rb") as fh:
        fh.seek(start)
        if start != 0:
            fh.readline()                    # align to a record boundary
        while fh.tell() < end:
            line = fh.readline()
            if not line:
                break
            try:
                r = orjson.loads(line)
            except Exception:
                continue
            if r.get("distance", 0.0) > MAX_DISTANCE:
                continue
            lab = _letters(r.get("word", ""))
            if not (MIN_LETTERS <= len(lab) <= MAX_LETTERS):
                continue
            d = r.get("data") or []
            if len(d) < MIN_POINTS:
                continue
            try:
                xs = np.fromiter((p["x"] for p in d), np.float64, len(d))
                ys = np.fromiter((p["y"] for p in d), np.float64, len(d))
                ts = np.fromiter((p["t"] for p in d), np.float64, len(d))
            except Exception:
                continue
            if not np.all(np.diff(ts) >= 0):
                order = np.argsort(ts, kind="stable")
                xs, ys, ts = xs[order], ys[order], ts[order]
            if not (np.isfinite(xs).all() and np.isfinite(ys).all()):
                continue
            if xs.min() < -0.5 or xs.max() > 1.5 or ys.min() < -0.5 or ys.max() > 1.5:
                continue
            rs = _resample(xs, ys, ts)
            if rs is None:
                continue
            x, y, dur = rs
            traces.append(np.stack([x, y], axis=1))
            labels.append(lab)
            lengths.append(len(lab))
            words.append(r.get("word", ""))
            durs.append(dur)
            sessions.append(r.get("session", ""))
    if not traces:
        return None
    return (np.stack(traces).astype(np.float16), b"".join(labels),
            np.array(lengths, np.int16), words,
            np.array(durs, np.float32), sessions)


def build(src: Path, out_dir: Path, workers: int = 16):
    out_dir.mkdir(parents=True, exist_ok=True)
    size = src.stat().st_size
    step = size // workers + 1
    chunks = [(str(src), i * step, min((i + 1) * step, size)) for i in range(workers)]

    with Pool(workers) as pool:
        parts = [p for p in pool.map(_worker, chunks) if p is not None]

    traces = np.concatenate([p[0] for p in parts])
    labels = np.frombuffer(b"".join(p[1] for p in parts), dtype=np.uint8)
    lengths = np.concatenate([p[2] for p in parts])
    words = [w for p in parts for w in p[3]]
    durs = np.concatenate([p[4] for p in parts])
    sessions = [s for p in parts for s in p[5]]

    offsets = np.zeros(len(lengths) + 1, np.int64)
    np.cumsum(lengths, out=offsets[1:])

    np.save(out_dir / "traces.npy", traces)
    np.save(out_dir / "labels.npy", labels)
    np.save(out_dir / "offsets.npy", offsets)
    np.save(out_dir / "durations.npy", durs)
    (out_dir / "words.txt").write_text("\n".join(words), encoding="utf-8")

    uniq = sorted(set(sessions))
    sid = {s: i for i, s in enumerate(uniq)}
    np.save(out_dir / "sessions.npy", np.array([sid[s] for s in sessions], np.int32))

    print(f"{src.name}: {len(traces):,} swipes, {len(uniq):,} sessions, "
          f"{traces.nbytes/1e6:.0f} MB traces, mean word len {lengths.mean():.2f}")


if __name__ == "__main__":
    root = Path("data/hf")
    for name, src in [("train", root / "train.jsonl"),
                      ("dev", root / "dev.jsonl"),
                      ("test", root / "test.jsonl")]:
        if src.exists():
            build(src, Path("data/packed") / name, workers=int(sys.argv[1]) if len(sys.argv) > 1 else 16)
