"""Synthetic gesture generation.

The point of this module is to produce traces whose *failure modes* look like a
real thumb's, not merely traces that pass through the right keys. Four things
matter, roughly in order of how much they change what a model learns:

1. **Timing.** Fingers obey the 2/3 power law -- speed falls as curvature rises
   (v proportional to kappa^-1/3) -- and they slow further on letters they mean
   to hit. This is the cue the geometric decoder throws away at resampling time,
   and the main reason a learned model has headroom.

2. **Doubled letters.** "putt" and "put" trace the same polyline. The only thing
   that separates them on a real device is that the finger *dwells* on the
   repeated key. Synthesizing that dwell is what gives a model any chance at the
   error class the geometric decoder cannot touch even in principle.

3. **Corner cutting.** A moving finger rounds corners toward the inside of the
   turn, and rounds them harder the faster it goes. Straight polylines through
   key centres are the single most unrealistic thing a naive generator does.

4. **Structured spatial error.** Not iid Gaussian: there is a per-gesture
   systematic bias (how the hand is holding the phone) on top of per-point
   tremor, and error grows with speed.
"""

from __future__ import annotations

import numpy as np

from .keymap import KeyMap


class SynthConfig:
    """Distortion *ceilings*, reached at sloppiness = 1.0.

    Every spatial error source scales with a single `sloppy` dial in [0, 1] so
    that results can be reported as a curve rather than a point. That matters
    because there is no ground truth for how sloppy a real thumb is -- and if
    the noise level is a free parameter, tuning it until the incumbent looks
    good (or bad) is trivially easy and completely meaningless. A model that
    wins across the whole dial wins regardless of where reality sits on it.

    Timing parameters are *not* scaled: they describe the device and the hand,
    not the error.
    """

    # spatial error ceilings, in units of one key width
    bias_sigma = 0.30          # whole-gesture systematic offset (how you hold it)
    via_sigma_base = 0.05      # floor on per-via-point scatter
    via_sigma_span = 0.55
    tremor_sigma = 0.05        # per-sample jitter
    corner_cut_max = 0.55      # fraction an interior via slides toward the chord
    endpoint_trim = 0.12       # fraction of path missing at touch-down / lift-off

    # timing (not scaled by sloppiness)
    speed_px_per_s = (480.0, 2200.0)   # mean, not peak
    power_law_beta = 1.0 / 3.0         # v ~ kappa^-beta
    via_dwell = (0.10, 0.55)           # speed floor at intended letters
    double_dwell = (2.5, 7.0)          # how much deeper a repeated letter dwells

    # device
    sample_hz = (60.0, 240.0)
    sample_jitter = 0.22
    min_point_spacing_px = 1.5         # matches SwipeTrace.MIN_POINT_SPACING_PX


def _catmull_rom(points: np.ndarray, samples_per_seg: int = 24) -> np.ndarray:
    """Centripetal Catmull-Rom through `points`; returns a dense polyline.

    Centripetal (alpha=0.5) rather than uniform: uniform Catmull-Rom forms cusps
    and self-intersections when consecutive control points are close together,
    which is exactly what happens on a doubled letter.
    """
    n = len(points)
    if n < 2:
        return points.copy()
    if n == 2:
        t = np.linspace(0, 1, samples_per_seg)[:, None]
        return points[0] + (points[1] - points[0]) * t

    # pad the ends so every real segment has four control points
    pad = np.vstack([points[0] + (points[0] - points[1]), points, points[-1] + (points[-1] - points[-2])])

    out = []
    alpha = 0.5
    for i in range(len(pad) - 3):
        p0, p1, p2, p3 = pad[i], pad[i + 1], pad[i + 2], pad[i + 3]

        def tj(ti, a, b):
            d = np.linalg.norm(b - a)
            return ti + (d ** alpha if d > 1e-9 else 1e-9)

        t0 = 0.0
        t1 = tj(t0, p0, p1)
        t2 = tj(t1, p1, p2)
        t3 = tj(t2, p2, p3)

        t = np.linspace(t1, t2, samples_per_seg, endpoint=False)[:, None]
        a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1
        a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2
        a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3
        b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2
        b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
        out.append((t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2)

    out.append(points[-1][None, :])
    return np.vstack(out)


def _curvature(path: np.ndarray) -> np.ndarray:
    """Menger curvature at each vertex of a polyline."""
    n = len(path)
    k = np.zeros(n)
    if n < 3:
        return k
    a, b, c = path[:-2], path[1:-1], path[2:]
    ab = np.linalg.norm(b - a, axis=1)
    bc = np.linalg.norm(c - b, axis=1)
    ca = np.linalg.norm(a - c, axis=1)
    # 4 * area / (|ab| |bc| |ca|)
    cross = np.abs((b[:, 0] - a[:, 0]) * (c[:, 1] - a[:, 1]) - (b[:, 1] - a[:, 1]) * (c[:, 0] - a[:, 0]))
    denom = ab * bc * ca
    good = denom > 1e-9
    k[1:-1][good] = (2.0 * cross[good]) / denom[good]
    k[0] = k[1]
    k[-1] = k[-2]
    return k


def synth_gesture(word: str, km: KeyMap, rng: np.random.Generator,
                  sloppy: float = 0.45, cfg=SynthConfig):
    """Return (xy, t_ms) for one synthetic gesture of `word`.

    `sloppy` in [0, 1] scales every spatial error source; 0 gives a machine-
    perfect trace through the key centres.

    `xy` is (N, 2) float32 pixels; `t_ms` is (N,) float32 milliseconds from touch
    down. Both have already been through the app's minimum-spacing filter, so
    they are what SwipeTrace would actually hold.
    """
    sloppy = float(np.clip(sloppy, 0.0, 1.0))
    letters = [ord(c) - 97 for c in word.lower() if "a" <= c <= "z"]
    if len(letters) < 2:
        return None
    unit = km.unit

    # --- control points ---------------------------------------------------
    centers = km.centers()[letters]                       # (L, 2)
    bias = rng.normal(0.0, cfg.bias_sigma * sloppy * unit, size=2)
    via_s = rng.uniform(0.0, cfg.via_sigma_base + cfg.via_sigma_span * sloppy) * unit
    pts = centers + bias + rng.normal(0.0, via_s, size=centers.shape)

    # mark which control points are a repeat of the previous letter
    is_double = np.zeros(len(letters), dtype=bool)
    for i in range(1, len(letters)):
        if letters[i] == letters[i - 1]:
            is_double[i] = True

    # a repeated key needs two *distinct* control points or the spline collapses;
    # nudge the second one off in a random direction, the way a real small loop does
    for i in np.flatnonzero(is_double):
        theta = rng.uniform(0, 2 * np.pi)
        pts[i] = pts[i] + np.array([np.cos(theta), np.sin(theta)]) * unit * rng.uniform(0.12, 0.34)

    # --- corner cutting ---------------------------------------------------
    cut = rng.uniform(0.0, cfg.corner_cut_max * sloppy)
    if len(pts) > 2:
        chord_mid = 0.5 * (pts[:-2] + pts[2:])
        interior = pts[1:-1]
        # do not cut a doubled-letter loop away; that dwell is the whole signal
        w = np.where(is_double[1:-1, None], 0.0, cut)
        pts[1:-1] = interior + (chord_mid - interior) * w

    # --- dense smooth path ------------------------------------------------
    dense = _catmull_rom(pts, samples_per_seg=32)
    seg = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    keep = np.concatenate([[True], seg > 1e-6])
    dense = dense[keep]
    if len(dense) < 3:
        return None

    seg = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(seg)])
    total = s[-1]
    if total < 1e-3:
        return None

    # --- speed profile ----------------------------------------------------
    kappa = _curvature(dense)
    v_max = rng.uniform(*cfg.speed_px_per_s)
    # 2/3 power law: slow into curves
    v = (kappa * unit + 0.05) ** (-cfg.power_law_beta)
    v = v / v.max()

    # deceleration at intended letters, deeper on a repeated one
    floor = rng.uniform(*cfg.via_dwell)
    dbl = rng.uniform(*cfg.double_dwell)
    envelope = np.ones_like(v)
    for i, p in enumerate(pts):
        j = int(np.argmin(np.linalg.norm(dense - p, axis=1)))
        width = unit * rng.uniform(0.22, 0.45) * (1.6 if is_double[i] else 1.0)
        depth = floor / dbl if is_double[i] else floor
        dip = 1.0 - (1.0 - depth) * np.exp(-0.5 * ((s - s[j]) / max(width, 1e-3)) ** 2)
        # envelope, not product: overlapping dips from adjacent letters must not
        # multiply together or a five-letter word decays to floor**5.
        envelope = np.minimum(envelope, dip)
    v *= envelope

    # global ease in/out
    u = s / total
    v *= 0.35 + 0.65 * np.sin(np.pi * np.clip(u, 0, 1)) ** 0.5

    v = np.clip(v, 1e-3, None)

    # integrate dt = ds / v  (trapezoid on the segment midpoints)
    v_mid = 0.5 * (v[:-1] + v[1:])
    dt = seg / v_mid
    t = np.concatenate([[0.0], np.cumsum(dt)])

    # Pin the *duration* rather than the mean speed. Duration is the integral of
    # ds/v, which the dwell dips dominate, so normalising v.mean() leaves long
    # words crawling. Rescaling time directly makes v_max mean what it says.
    if t[-1] > 1e-9:
        t = t * (total / v_max) / t[-1]
    t = t * 1000.0                                          # ms

    # --- trim endpoints ---------------------------------------------------
    lag = rng.uniform(0.0, cfg.endpoint_trim * sloppy) * total
    lead = rng.uniform(0.0, cfg.endpoint_trim * sloppy) * total
    lo, hi = float(lag), float(total - lead)
    if hi - lo < unit * 0.5:
        lo, hi = 0.0, float(total)

    # --- device sampling --------------------------------------------------
    hz = rng.uniform(*cfg.sample_hz)
    interval = 1000.0 / hz
    t_lo = float(np.interp(lo, s, t))
    t_hi = float(np.interp(hi, s, t))
    duration = max(t_hi - t_lo, 1.0)

    n_samples = max(int(duration / interval) + 1, 2)
    ticks = t_lo + np.arange(n_samples) * interval
    ticks += rng.normal(0.0, cfg.sample_jitter * interval, size=n_samples)
    ticks = np.clip(np.sort(ticks), t_lo, t_hi)

    sx = np.interp(ticks, t, dense[:, 0])
    sy = np.interp(ticks, t, dense[:, 1])

    # per-sample tremor
    tremor = cfg.tremor_sigma * sloppy * unit
    sx += rng.normal(0.0, tremor, size=sx.shape)
    sy += rng.normal(0.0, tremor, size=sy.shape)

    xy = np.stack([sx, sy], axis=1)
    t_ms = ticks - t_lo

    # --- the app's own minimum-spacing filter -----------------------------
    keep = [0]
    last = xy[0]
    for i in range(1, len(xy)):
        if np.linalg.norm(xy[i] - last) >= cfg.min_point_spacing_px:
            keep.append(i)
            last = xy[i]
    if len(keep) < 2:
        return None
    xy = xy[keep]
    t_ms = t_ms[keep]

    return xy.astype(np.float32), t_ms.astype(np.float32)
