"""CPU-only shape and sanity tests for the swipe encoder.

These run in CI without the FUTO dataset or a GPU. They guard the structural
contract the Kotlin port (SwipeNet.kt) transcribes by hand: tensor shapes,
the log-distribution property of the emissions, and the DCT basis layout.
"""
import math
import sys
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from swipe.model import (  # noqa: E402
    N_COEFF,
    N_DCT,
    T_IN,
    T_OUT,
    SwipeEncoder,
    TrajectoryFeatures,
    _savgol_kernels,
    dct_basis,
)


def test_savgol_kernels_shape():
    k = _savgol_kernels()
    assert k.shape == (3, 7)
    # smoothing row sums to 1 (it is an averaging kernel)
    assert abs(k[0].sum().item() - 1.0) < 1e-6
    # derivative rows sum to ~0 (constants vanish under differentiation)
    assert abs(k[1].sum().item()) < 1e-6
    assert abs(k[2].sum().item()) < 1e-5


def test_features_output_shape():
    m = TrajectoryFeatures().eval()
    xy = torch.rand(2, 2, T_IN)
    with torch.no_grad():
        feats = m(xy)
    assert feats.shape == (2, 8, T_IN)
    assert torch.isfinite(feats).all()


def test_encoder_forward_shapes_and_distribution():
    m = SwipeEncoder().eval()
    B, K = 2, 26
    xy = torch.rand(B, 2, T_IN).clamp(0, 1)
    keys = torch.rand(B, K, 2)
    with torch.no_grad():
        logp, gate = m(xy, keys)
    assert logp.shape == (B, T_OUT, K + 1)
    assert gate.shape == (B, T_OUT)
    assert torch.isfinite(logp).all()
    # emissions are a log distribution: exp(logsumexp) == 1 per timestep
    mass = logp.logsumexp(-1).exp()
    assert torch.allclose(mass, torch.ones_like(mass), atol=1e-5)
    # gate is a probability
    assert ((gate >= 0) & (gate <= 1)).all()


def test_dct_basis_shape_and_range():
    keys = torch.rand(3, 26, 2)
    phi = dct_basis(keys)
    assert phi.shape == (3, 26, N_COEFF)
    assert N_COEFF == N_DCT * N_DCT
    assert phi.abs().max().item() <= 1.0 + 1e-6
    # DC coefficient is all ones: cos(0)*cos(0)
    assert torch.allclose(phi[..., 0], torch.ones(3, 26), atol=1e-6)


def test_encoder_zero_init_heads():
    m = SwipeEncoder()
    assert (m.coeff.weight == 0).all() and (m.coeff.bias == 0).all()
    assert (m.gate.weight == 0).all() and (m.gate.bias == 0).all()
    # every key logit starts at 0, lambda at 0.5
    xy = torch.rand(1, 2, T_IN).clamp(0, 1)
    keys = torch.rand(1, 4, 2)
    with torch.no_grad():
        logp, gate = m(xy, keys)
    assert torch.allclose(gate, torch.full_like(gate, 0.5), atol=1e-6)
    # with zero spatial pattern, chars share mass equally: log(0.5/4) each
    expect = math.log(0.5 / 4)
    assert torch.allclose(logp[0, 0, :4], torch.full((4,), expect), atol=1e-5)
