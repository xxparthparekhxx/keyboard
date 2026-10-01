"""Format guard for the flat binaries tools/export_weights.py writes.

export_weights.py is a top-to-bottom script (it parses arguments, loads the
checkpoint and writes the files at import level) and exposes no standalone
write/read helper, so a full round-trip through it would need a checkpoint.
Instead this module pins the documented layout itself:

  SWEN: magic 'SWEN', version 1, tensor count, then per tensor
        [name_len:i32][name:utf8][ndim:i32][dims:i32...][data:f32...],
        all little-endian (export_weights.py docstring and write loop).
  SWRF: magic 'SWRF', version 1, then flat f32 arrays each prefixed by an
        i32 size (the ``_write_parity_fixture`` helper).

A synthetic weights set is round-tripped through that layout in ``tmp_path``,
and the committed ``swipe_encoder.bin`` / ``swipe_reference.bin`` artifacts
are parsed with the same reader, so any future change of the exporter's struct
packing shows up here against the real files. No FUTO dataset or GPU needed.
"""
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from swipe.model import SwipeEncoder, T_IN, T_OUT  # noqa: E402

ML_ROOT = Path(__file__).resolve().parent.parent
ENCODER_BIN = ML_ROOT.parent / "app" / "src" / "main" / "assets" / "swipe_encoder.bin"
PARITY_FIXTURE = ML_ROOT.parent / "app" / "src" / "test" / "resources" / "swipe_reference.bin"


def _write_swem(path, tensors):
    """Mirror of the write loop in export_weights.py (lines 59-67)."""
    with open(path, "wb") as f:
        f.write(b"SWEN")
        f.write(struct.pack("<ii", 1, len(tensors)))
        for name, arr in tensors.items():
            nb = name.encode()
            f.write(struct.pack("<i", len(nb)))
            f.write(nb)
            f.write(struct.pack("<i", arr.ndim))
            f.write(struct.pack(f"<{arr.ndim}i", *arr.shape))
            f.write(arr.tobytes())


def _read_swem(buf):
    """Inverse of _write_swem; raises (via assertions) on any layout drift."""
    magic = buf[0:4]
    version, count = struct.unpack_from("<ii", buf, 4)
    off = 12
    tensors = {}
    for _ in range(count):
        (name_len,) = struct.unpack_from("<i", buf, off)
        off += 4
        name = buf[off:off + name_len].decode("utf-8")
        off += name_len
        (ndim,) = struct.unpack_from("<i", buf, off)
        off += 4
        dims = struct.unpack_from(f"<{ndim}i", buf, off)
        off += 4 * ndim
        n = int(np.prod(dims))
        raw = buf[off:off + 4 * n]
        assert len(raw) == 4 * n, f"truncated data for tensor {name!r}"
        off += 4 * n
        tensors[name] = np.frombuffer(raw, dtype="<f4").reshape(dims).copy()
    assert off == len(buf), f"{len(buf) - off} trailing bytes after the last tensor"
    return magic, version, count, tensors


def test_swem_round_trip_synthetic(tmp_path):
    tensors = {
        "alpha": np.arange(6, dtype=np.float32).reshape(2, 3),
        "beta": np.array([1.5, -2.25], dtype=np.float32),
        "gamma": np.zeros((4, 4), dtype=np.float32),
    }
    out = tmp_path / "weights.bin"
    _write_swem(out, tensors)
    magic, version, count, parsed = _read_swem(out.read_bytes())
    assert magic == b"SWEN"
    assert version == 1
    assert count == 3
    assert set(parsed) == set(tensors)
    for name, expected in tensors.items():
        got = parsed[name]
        assert got.dtype == np.float32
        assert got.shape == expected.shape
        assert np.array_equal(got, expected)


def test_shipped_encoder_bin_matches_documented_format():
    assert ENCODER_BIN.exists(), f"missing {ENCODER_BIN}"
    magic, version, count, tensors = _read_swem(ENCODER_BIN.read_bytes())
    assert magic == b"SWEN"
    assert version == 1
    assert count == len(tensors)
    # Same naming template as the put() calls in export_weights.py, with the
    # block count taken from the model itself (dilations 1,2,3,5,8 -> 5 blocks).
    model = SwipeEncoder()
    per_block = ("dw.w", "dw.b", "pw1.w", "pw1.b", "grn.g", "grn.b",
                 "pw2.w", "pw2.b", "se1.w", "se1.b", "se2.w", "se2.b")
    expected = {"savgol", "stem.w", "stem.b",
                *(f"b{i}.{part}" for i in range(len(model.blocks)) for part in per_block),
                "adapter.w", "adapter.b", "coeff.w", "coeff.b", "gate.w", "gate.b"}
    assert set(tensors) == expected
    assert tensors["savgol"].shape == (3, 7)
    for name, arr in tensors.items():
        assert arr.dtype == np.float32, name
        assert all(d > 0 for d in arr.shape), name


def test_parity_fixture_matches_documented_format():
    assert PARITY_FIXTURE.exists(), f"missing {PARITY_FIXTURE}"
    buf = PARITY_FIXTURE.read_bytes()
    assert buf[:4] == b"SWRF"
    (version,) = struct.unpack_from("<i", buf, 4)
    assert version == 1
    off = 8
    sizes = []
    while off < len(buf):
        (n,) = struct.unpack_from("<i", buf, off)
        off += 4
        assert n > 0
        off += 4 * n
        sizes.append(n)
    assert off == len(buf), "trailing bytes after the last float array"
    # _write_parity_fixture writes exactly (xy, keys, feats, logp, gate);
    # 26 keys come from torch.rand(1, 26, 2) in export_weights.py, 8 channels
    # from TrajectoryFeatures.
    n_keys = 26
    assert len(sizes) == 5
    assert sizes == [2 * T_IN, 2 * n_keys, 8 * T_IN, T_OUT * (n_keys + 1), T_OUT]
