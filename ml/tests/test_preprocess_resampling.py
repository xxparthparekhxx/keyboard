"""Parity guard for the time-uniform 64-point resampling in swipe.preprocess.

The Kotlin runtime mirrors ``_resample`` line by line (SwipeNeuralDecoder.kt,
``resampleUniformTime``): ``n60 = int(dur / 1000 * RESAMPLE_HZ) + 1`` (min 2)
points on ``linspace(0, dur)``, linear interpolation onto that 60 Hz grid, then
a second linear interpolation onto ``linspace(0, dur, T_IN=64)``. AGENTS.md
calls any divergence between the two sides a silent failure (worse predictions,
no crash), so the properties below pin the reference implementation.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from swipe.preprocess import T_IN, _resample  # noqa: E402


def test_resample_well_formed_trace_returns_exactly_64_points():
    ts = np.array([0.0, 3.0, 41.0, 97.0, 180.0])
    xs = np.array([0.0, 0.2, 0.45, 0.7, 1.0])
    ys = np.array([1.0, 0.8, 0.5, 0.3, 0.1])
    out = _resample(xs, ys, ts)
    assert out is not None
    x, y, dur = out
    assert x.shape == (T_IN,) and y.shape == (T_IN,)
    assert x.dtype == np.float32 and y.dtype == np.float32
    assert abs(dur - 180.0) < 1e-6


def test_resample_output_time_grid_is_monotonic_and_bounded():
    # The 64 output points are defined on linspace(0, dur, T_IN) inside
    # _resample (relative to t0 = ts[0]), so in absolute input time they span
    # exactly [ts[0], ts[-1]] -- never outside the input's span.
    ts = np.array([5.0, 11.0, 57.0, 129.0, 201.0])
    xs = np.array([0.0, 0.1, 0.6, 0.8, 0.9])
    ys = np.array([0.0, 0.4, 0.2, 0.7, 0.5])
    x, y, dur = _resample(xs, ys, ts)
    g = np.linspace(0.0, dur, T_IN)
    assert np.all(np.diff(g) >= 0)
    assert g[0] == 0.0 and g[-1] == dur
    assert dur == ts[-1] - ts[0]
    assert np.all(g + ts[0] >= ts[0] - 1e-9) and np.all(g + ts[0] <= ts[-1] + 1e-9)


def test_resample_preserves_first_and_last_input_points():
    # g60 and g both start at 0 and end at dur, and np.interp at the first/
    # last node of its grid returns the first/last input value, so the
    # endpoints of a non-linear trace survive untouched (_resample, lines 43-48).
    ts = np.array([0.0, 20.0, 60.0, 90.0])
    xs = np.array([0.0, 0.9, 0.1, 1.0])
    ys = np.array([0.0, 0.2, 0.8, 0.4])
    x, y, _ = _resample(xs, ys, ts)
    assert abs(x[0] - xs[0]) < 1e-6 and abs(x[-1] - xs[-1]) < 1e-6
    assert abs(y[0] - ys[0]) < 1e-6 and abs(y[-1] - ys[-1]) < 1e-6


def test_resample_linear_in_time_input_comes_out_uniform_in_time():
    # x and y are each a straight line in t, so the double interpolation is
    # exact and the output must equal the uniform-time grid itself (any move to
    # arc-length spacing would still pass this, but fail the dwell test below).
    dur = 500.0
    ts = np.array([0.0, 40.0, 190.0, 333.0, 500.0])
    xs = ts / 100.0
    ys = 1.0 - ts / dur
    x, y, _ = _resample(xs, ys, ts)
    expect_x = np.linspace(0.0, dur / 100.0, T_IN)
    expect_y = np.linspace(1.0, 0.0, T_IN)
    assert np.max(np.abs(x - expect_x)) < 1e-5
    assert np.max(np.abs(y - expect_y)) < 1e-5


def test_resample_hand_computed_tiny_example():
    # 4 input points, worked out by hand from _resample's two interpolation
    # passes: dur = 100 -> n60 = int(100/1000*60) + 1 = 7, so the 60 Hz grid is
    # [0, 50/3, 100/3, 50, 200/3, 250/3, 100] ms and the sampled path is
    #   x60 = [0,   2/3,  4/3, 2,   7/3,  8/3,  3 ]
    #   y60 = [0,   2/3,  4/3, 2,   8/3,  10/3, 4 ]
    # g = linspace(0, 100, 64): g[1] = 100/63 sits in the first 60 Hz segment
    # (slope 1/25 per ms on both axes) -> x = y = 100/63 / 25 = 4/63.
    # g[42] = 42*100/63 = 200/3 lands exactly on the 5th 60 Hz node ->
    # x = 7/3, y = 8/3. Endpoints survive.
    ts = np.array([0.0, 25.0, 50.0, 100.0])
    xs = np.array([0.0, 1.0, 2.0, 3.0])
    ys = np.array([0.0, 1.0, 2.0, 4.0])
    x, y, _ = _resample(xs, ys, ts)
    assert abs(x[1] - 4.0 / 63.0) < 1e-6
    assert abs(y[1] - 4.0 / 63.0) < 1e-6
    assert abs(x[42] - 7.0 / 3.0) < 1e-6
    assert abs(y[42] - 8.0 / 3.0) < 1e-6
    assert abs(x[0]) < 1e-6 and abs(x[-1] - 3.0) < 1e-6
    assert abs(y[0]) < 1e-6 and abs(y[-1] - 4.0) < 1e-6


def test_resample_dwell_concentrates_output_points_on_plateau():
    # The pen moves 0 -> 10, hesitates at 10 for 80% of the duration, then
    # 10 -> 20. Time-uniform resampling must keep the hesitation visible: the
    # 60 Hz grid pins the flat part to [50/3, 250/3] ms, so the g points
    # i = 11..52 (g[i] = 100*i/63 falls inside that window) land exactly on
    # the plateau -> 42 of 64 output points sit on x == 10, a clear majority.
    # An arc-length resampler would put zero points there (the plateau has no
    # spatial extent), which is exactly the divergence this guards against.
    ts = np.array([0.0, 10.0, 30.0, 50.0, 70.0, 90.0, 100.0])
    xs = np.array([0.0, 10.0, 10.0, 10.0, 10.0, 10.0, 20.0])
    ys = np.zeros(7)
    x, _, _ = _resample(xs, ys, ts)
    plateau = np.isclose(x, 10.0, atol=1e-6)
    assert plateau.sum() == 42
    assert plateau.sum() > (~plateau).sum()
    # The median output point is on the dwelt position, not between the moves.
    assert np.median(x) == 10.0
