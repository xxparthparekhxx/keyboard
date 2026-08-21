"""Faithful Python port of SwipeDecoder.kt -- the incumbent, for honest comparison.

Constants and control flow mirror the Kotlin line for line so that "the model
beats the baseline" means something. Verified against the real Kotlin decoder in
tools/verify_parity.
"""
from __future__ import annotations

import numpy as np

from .keymap import KeyMap
from .lexicon import Lexicon

SAMPLES = 32
MIN_TRACE_KEY_WIDTHS = 0.7
START_RADIUS = 1.25
END_RADIUS = 1.40
MAX_ENDPOINT_CANDIDATES = 4
RELAXED_SCALE = 1.55
RELAXED_ENDPOINT_CANDIDATES = 6
CORRIDOR = 1.15
PIVOT_RADIUS = 1.25
TUNNEL = 0.42
W_SHAPE = 1.90
W_LOCATION = 1.50
W_LENGTH = 0.35
W_FREQUENCY = 1.35
START_RANK_PENALTY = 0.22
END_RANK_PENALTY = 0.20
PIVOT_ANGLE = 1.08
MAX_PIVOTS = 8
REJECTED = float("inf")


def resample(xy: np.ndarray, count: int = SAMPLES):
    """Mirrors SwipeTrace.sample: `count` points spaced equally by arc length."""
    if len(xy) < 2 or count < 2:
        return None
    seg = np.linalg.norm(np.diff(xy, axis=0), axis=1)
    total = float(seg.sum())
    if total <= 0:
        return None
    s = np.concatenate([[0.0], np.cumsum(seg)])
    targets = np.linspace(0.0, total, count)
    out = np.empty((count, 2))
    out[:, 0] = np.interp(targets, s, xy[:, 0])
    out[:, 1] = np.interp(targets, s, xy[:, 1])
    return out, total


def _nearest(km: KeyMap, x, y, max_distance, limit):
    d = np.hypot(km.cx - x, km.cy - y)
    order = np.argsort(d, kind="stable")
    return [int(i) for i in order[:limit] if d[i] <= max_distance]


def _detect_pivots(pts, trace_length, unit):
    n = len(pts)
    step = trace_length / (n - 1)
    if step <= 0:
        return []
    window = int(unit * 0.6 / step)
    window = max(1, min(window, n // 5))
    if window < 1 or n < 2 * window + 3:
        return []

    found, sharpest, sharpest_at = [], 0.0, -1
    for i in range(window, n - window):
        a = pts[i] - pts[i - window]
        b = pts[i + window] - pts[i]
        na, nb = np.linalg.norm(a), np.linalg.norm(b)
        angle = 0.0
        if na > 0 and nb > 0:
            angle = float(np.arccos(np.clip(a @ b / (na * nb), -1.0, 1.0)))
        if angle >= PIVOT_ANGLE:
            if angle > sharpest:
                sharpest, sharpest_at = angle, i
        elif sharpest_at >= 0:
            if len(found) < MAX_PIVOTS:
                found.append(sharpest_at)
            sharpest, sharpest_at = 0.0, -1
    if sharpest_at >= 0 and len(found) < MAX_PIVOTS:
        found.append(sharpest_at)
    return found


def _ideal_path(letters, km, count):
    c = km.centers()[letters]
    seg = np.linalg.norm(np.diff(c, axis=0), axis=1)
    total = float(seg.sum())
    if total <= 0:
        return np.repeat(c[:1], count, axis=0), 0.0
    s = np.concatenate([[0.0], np.cumsum(seg)])
    targets = np.linspace(0.0, total, count)
    out = np.empty((count, 2))
    out[:, 0] = np.interp(targets, s, c[:, 0])
    out[:, 1] = np.interp(targets, s, c[:, 1])
    return out, total


def _score(pts, trace_length, km, letters, unit, corridor, pivots, pivot_radius, D):
    n = len(pts)
    L = len(letters)

    # --- corridor walk ---
    first = letters[0]
    drift = max(0.0, D[0, first] / unit - TUNNEL)
    cursor = 0
    for i in range(1, L - 1):
        key = letters[i]
        col = D[:, key]
        hit = -1
        j = cursor
        while j < n:
            if col[j] <= corridor:
                hit = j
                break
            j += 1
        if hit < 0:
            return REJECTED
        hit_d = col[hit]
        m = hit + 1
        while m < n and col[m] < hit_d:
            hit_d = col[m]
            hit = m
            m += 1
        drift += max(0.0, hit_d / unit - TUNNEL)
        cursor = hit
    drift += max(0.0, D[n - 1, letters[-1]] / unit - TUNNEL)
    location_cost = drift / L

    # --- pivot coverage ---
    for p in pivots:
        if not np.any(D[p, letters] <= pivot_radius):
            return REJECTED

    # --- shape ---
    ideal, ideal_len = _ideal_path(letters, km, n)
    shape_cost = float(np.linalg.norm(pts - ideal, axis=1).sum()) / (n * unit)

    # --- length plausibility ---
    length_cost = abs(ideal_len - trace_length) / (trace_length + unit)

    return W_SHAPE * shape_cost + W_LOCATION * location_cost + W_LENGTH * length_cost


def _decode_pass(pts, trace_length, km, lx: Lexicon, max_results, relaxed):
    unit = km.unit
    if unit <= 0 or trace_length < unit * MIN_TRACE_KEY_WIDTHS:
        return []
    n = len(pts)
    if n < 2:
        return []

    slack = RELAXED_SCALE if relaxed else 1.0
    limit = RELAXED_ENDPOINT_CANDIDATES if relaxed else MAX_ENDPOINT_CANDIDATES

    start_keys = _nearest(km, pts[0, 0], pts[0, 1], unit * START_RADIUS * slack, limit)
    if not start_keys:
        return []
    end_keys = _nearest(km, pts[-1, 0], pts[-1, 1], unit * END_RADIUS * slack, limit)
    if not end_keys:
        return []
    end_rank = {k: r for r, k in enumerate(end_keys)}

    pivots = [] if relaxed else _detect_pivots(pts, trace_length, unit)
    corridor = unit * CORRIDOR * slack
    pivot_radius = unit * PIVOT_RADIUS

    # distance from every sample point to every key, computed once
    D = np.hypot(pts[:, 0:1] - km.cx[None, :], pts[:, 1:2] - km.cy[None, :])

    results = []
    for s_rank, sk in enumerate(start_keys):
        start_bias = START_RANK_PENALTY * s_rank
        for wi in lx.buckets[sk]:
            letters = lx.keys[wi]
            L = len(letters)
            if L < 2 or L > 22:
                continue
            e_rank = end_rank.get(int(letters[-1]))
            if e_rank is None:
                continue
            cost = _score(pts, trace_length, km, letters, unit,
                          corridor, pivots, pivot_radius, D)
            if cost == REJECTED:
                continue
            prior = W_FREQUENCY * (lx.scores[wi] / 255.0)
            results.append((cost + start_bias + END_RANK_PENALTY * e_rank - prior, wi))

    if not results:
        return []
    results.sort(key=lambda r: r[0])
    return [wi for _, wi in results[:max_results]]


def decode(xy: np.ndarray, km: KeyMap, lx: Lexicon, max_results: int = 4):
    """Returns word indices, best first. Mirrors SwipeDecoder.decode."""
    r = resample(xy, SAMPLES)
    if r is None:
        return []
    pts, total = r
    strict = _decode_pass(pts, total, km, lx, max_results, relaxed=False)
    if strict:
        return strict
    return _decode_pass(pts, total, km, lx, max_results, relaxed=True)
