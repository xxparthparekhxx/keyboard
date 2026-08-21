"""Layout-agnostic swipe encoder (FUTO-style TCN + spectral spatial head).

The one idea that makes this layout-agnostic: the network never learns a
parameter per key. It emits a 2D spatial *pattern* as 64 cosine coefficients,
and the active keyboard is read out by sampling that pattern at each key's
(x, y). Swapping layouts is then just rebuilding a fixed basis matrix -- about
ten lines of arithmetic -- rather than retraining.
"""
from __future__ import annotations

import math

import torch
import torch.nn as nn
import torch.nn.functional as F

T_IN = 64
T_OUT = 32
N_DCT = 8                      # spatial resolution; N*N coefficients
N_COEFF = N_DCT * N_DCT


# --------------------------------------------------------------------------
# Input features
# --------------------------------------------------------------------------

def _savgol_kernels(window: int = 7, order: int = 2) -> torch.Tensor:
    """Savitzky-Golay smoothing / 1st / 2nd derivative kernels, as (3, window).

    Built by least squares rather than hard-coded so the window and order stay
    adjustable: fit a degree-`order` polynomial over the window and read off its
    derivatives at the centre.
    """
    half = window // 2
    p = torch.arange(-half, half + 1, dtype=torch.float64)
    A = torch.stack([p ** j for j in range(order + 1)], dim=1)     # (window, order+1)
    pinv = torch.linalg.pinv(A)                                    # (order+1, window)
    return torch.stack([pinv[0], pinv[1], pinv[2] * 2.0]).float()  # d0, d1, d2*2!


class TrajectoryFeatures(nn.Module):
    """(B, 2, T) raw coordinates -> (B, 8, T) features.

    Derived *after* augmentation, so velocity/curvature describe the augmented
    geometry. Kept as a module (rather than done in the data pipeline) so the
    exported graph carries it and the Kotlin port has one obvious thing to
    mirror.
    """

    def __init__(self, window: int = 7, order: int = 2):
        super().__init__()
        self.window = window
        k = _savgol_kernels(window, order)                  # (3, window)
        self.register_buffer("kern", k.flip(-1).unsqueeze(1))  # conv == correlation

    def forward(self, xy: torch.Tensor) -> torch.Tensor:
        B, _, T = xy.shape
        pad = self.window // 2
        flat = xy.reshape(B * 2, 1, T)
        flat = F.pad(flat, (pad, pad), mode="replicate")
        out = F.conv1d(flat, self.kern)                     # (B*2, 3, T)
        out = out.reshape(B, 2, 3, T)

        pos = out[:, :, 0]                                  # (B, 2, T) smoothed x,y
        vel = out[:, :, 1]
        acc = out[:, :, 2]

        speed = torch.sqrt(vel[:, 0] ** 2 + vel[:, 1] ** 2 + 1e-8).unsqueeze(1)

        # curvature: rate of change of heading, unwrapped to (-pi, pi] then clamped
        theta = torch.atan2(vel[:, 1], vel[:, 0])
        dtheta = torch.zeros_like(theta)
        dtheta[:, 1:] = theta[:, 1:] - theta[:, :-1]
        dtheta = torch.remainder(dtheta + math.pi, 2 * math.pi) - math.pi
        curv = dtheta.clamp(-2.0, 2.0).unsqueeze(1)

        return torch.cat([pos, vel, acc, speed, curv], dim=1)   # (B, 8, T)


# --------------------------------------------------------------------------
# Backbone
# --------------------------------------------------------------------------

class GRN(nn.Module):
    """Global response normalization (ConvNeXt V2), over the time axis."""

    def __init__(self, dim: int):
        super().__init__()
        self.gamma = nn.Parameter(torch.zeros(1, 1, dim))
        self.beta = nn.Parameter(torch.zeros(1, 1, dim))

    def forward(self, x):                                    # (B, T, C)
        gx = torch.linalg.norm(x, dim=1, keepdim=True)        # (B, 1, C)
        nx = gx / (gx.mean(dim=-1, keepdim=True) + 1e-6)
        return self.gamma * (x * nx) + self.beta + x


class SqueezeExcite(nn.Module):
    def __init__(self, dim: int, reduction: int = 4):
        super().__init__()
        hidden = max(dim // reduction, 8)
        self.fc1 = nn.Linear(dim, hidden)
        self.fc2 = nn.Linear(hidden, dim)

    def forward(self, x):                                    # (B, T, C)
        s = x.mean(dim=1)
        s = torch.sigmoid(self.fc2(F.relu(self.fc1(s))))
        return x * s.unsqueeze(1)


class TCNBlock(nn.Module):
    """ConvNeXt block adapted to 1D: dilated depthwise conv, BN, GLU MLP, GRN, SE."""

    def __init__(self, dim: int, dilation: int, expansion: int = 4,
                 kernel: int = 7, dropout: float = 0.1):
        super().__init__()
        pad = dilation * (kernel - 1) // 2
        self.dw = nn.Conv1d(dim, dim, kernel, padding=pad, dilation=dilation, groups=dim)
        self.bn = nn.BatchNorm1d(dim)
        hidden = expansion * dim
        self.pw1 = nn.Linear(dim, hidden)          # -> GLU halves it to hidden//2
        self.grn = GRN(hidden // 2)
        self.pw2 = nn.Linear(hidden // 2, dim)
        self.se = SqueezeExcite(dim)
        self.drop = nn.Dropout(dropout)

    def forward(self, x):                                    # (B, T, C)
        residual = x
        h = self.bn(self.dw(x.transpose(1, 2))).transpose(1, 2)
        h = F.glu(self.pw1(h), dim=-1)
        h = self.grn(h)
        h = self.pw2(h)
        h = self.se(h)
        return residual + self.drop(h)


# --------------------------------------------------------------------------
# Spectral spatial head
# --------------------------------------------------------------------------

def dct_basis(keys: torch.Tensor, n: int = N_DCT) -> torch.Tensor:
    """Fixed separable cosine basis Phi.

    keys: (..., K, 2) key centres normalized to [0, 1]^2.
    returns: (..., K, n*n) with Phi[k, (u,v)] = cos(pi u x_k) cos(pi v y_k).

    Computed once per layout and reused for every timestep -- and cheap enough
    to recompute on device whenever the keyboard is resized.
    """
    u = torch.arange(n, device=keys.device, dtype=keys.dtype)
    cx = torch.cos(math.pi * keys[..., 0:1] * u)             # (..., K, n)
    cy = torch.cos(math.pi * keys[..., 1:2] * u)             # (..., K, n)
    return (cx.unsqueeze(-1) * cy.unsqueeze(-2)).flatten(-2)  # (..., K, n*n)


class SwipeEncoder(nn.Module):
    def __init__(self, dim: int = 128, dilations=(1, 2, 3, 5, 8),
                 expansion: int = 4, head_dim: int = 256, dropout: float = 0.1):
        super().__init__()
        self.features = TrajectoryFeatures()
        self.stem = nn.Conv1d(8, dim, kernel_size=5, padding=2)
        self.blocks = nn.ModuleList(
            [TCNBlock(dim, d, expansion, dropout=dropout) for d in dilations])
        # 2x adapter: halves the time axis, widens into the head
        self.adapter = nn.Conv1d(dim, head_dim, kernel_size=2, stride=2)
        self.adapter_bn = nn.BatchNorm1d(head_dim)
        self.coeff = nn.Linear(head_dim, N_COEFF)
        self.gate = nn.Linear(head_dim, 1)
        # zero-init both heads: every key logit starts at 0 and lambda at 0.5
        nn.init.zeros_(self.coeff.weight); nn.init.zeros_(self.coeff.bias)
        nn.init.zeros_(self.gate.weight); nn.init.zeros_(self.gate.bias)

    def trunk(self, xy: torch.Tensor):
        """(B, 2, T_IN) -> coefficients (B, T_OUT, 64), gate logits (B, T_OUT)."""
        h = self.stem(self.features(xy)).transpose(1, 2)     # (B, T, C)
        for blk in self.blocks:
            h = blk(h)
        h = self.adapter_bn(self.adapter(h.transpose(1, 2))).transpose(1, 2)
        return self.coeff(h), self.gate(h).squeeze(-1)

    def forward(self, xy: torch.Tensor, keys: torch.Tensor):
        """Log emissions over K+1 classes (blank last), shape (B, T_OUT, K+1)."""
        coeff, gate_logit = self.trunk(xy)
        phi = dct_basis(keys)                                 # (B, K, 64)
        z = torch.einsum("btc,bkc->btk", coeff, phi)          # (B, T, K)

        log_lambda = F.logsigmoid(gate_logit).unsqueeze(-1)   # log lambda
        log_blank = F.logsigmoid(-gate_logit).unsqueeze(-1)   # log(1 - lambda)
        log_chars = F.log_softmax(z, dim=-1) + log_lambda
        return torch.cat([log_chars, log_blank], dim=-1), torch.sigmoid(gate_logit)


if __name__ == "__main__":
    m = SwipeEncoder()
    n = sum(p.numel() for p in m.parameters())
    print(f"encoder parameters: {n:,}   (paper: 635K)")
    for name, mod in [("stem", m.stem), ("blocks", m.blocks), ("adapter", m.adapter),
                      ("coeff", m.coeff), ("gate", m.gate)]:
        print(f"   {name:8s} {sum(p.numel() for p in mod.parameters()):>9,}")
    xy = torch.randn(4, 2, T_IN).clamp(0, 1)
    keys = torch.rand(4, 26, 2)
    lp, lam = m(xy, keys)
    print("log-emissions", tuple(lp.shape), "gate", tuple(lam.shape))
    print("sanity: exp(logsumexp) =", lp.logsumexp(-1).exp().mean().item(), "(should be 1.0)")
