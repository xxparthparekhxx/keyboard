"""CPU-only tests for dataset packing helpers (no downloads needed)."""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from swipe.preprocess import _letters, _resample, T_IN  # noqa: E402


def test_letters_maps_to_indices():
    assert _letters("cat") == bytes([2, 0, 19])
    assert _letters("Don't") == bytes([3, 14, 13, 19])  # case folded, ' skipped


def test_resample_returns_fixed_points():
    ts = np.arange(10) * 16.0
    xs = np.linspace(0.0, 1.0, 10)
    ys = np.linspace(1.0, 0.0, 10)
    out = _resample(xs, ys, ts)
    assert out is not None
    x, y, dur = out
    assert x.shape == (T_IN,) and y.shape == (T_IN,)
    assert x.dtype == np.float32
    assert abs(dur - 144.0) < 1e-3
    # endpoints pinned to the input range
    assert abs(x[0] - 0.0) < 1e-6 and abs(x[-1] - 1.0) < 1e-6


def test_resample_rejects_degenerate():
    xs = np.array([0.0, 1.0])
    ys = np.array([0.0, 1.0])
    assert _resample(xs, ys, np.array([100.0, 100.0])) is None  # zero duration
    assert _resample(xs, ys, np.array([100.0, 100.0 + 20001.0])) is None  # too long
