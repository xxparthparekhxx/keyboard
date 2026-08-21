"""Coordinated trajectory + layout augmentation, on GPU, per batch.

Every geometric stage is applied *identically* to the trajectory and to the
layout-key tensor, so the augmented keyboard stays consistent with the augmented
swipe -- the image/bounding-box co-augmentation trick. This is what stops the
encoder from memorising where QWERTY's keys happen to be and forces it to read
intention out of the gesture itself.

Rotation runs over the full circle and both flips are coin-flips, which sounds
far too aggressive until you notice that's precisely the point: a model that
still works upside down cannot have learned "e is near the top left".
"""
from __future__ import annotations

import math

import torch


def _u(b: int, lo: float, hi: float, device) -> torch.Tensor:
    return torch.rand(b, device=device) * (hi - lo) + lo


def co_augment(xy: torch.Tensor, keys: torch.Tensor, radii: torch.Tensor | None = None,
               p_reverse: float = 0.1):
    """xy: (B,2,T) in [0,1]^2, keys: (B,K,2). Returns (xy, keys, radii, reversed_mask)."""
    B, _, T = xy.shape
    dev = xy.device
    p = xy.transpose(1, 2).clone()                       # (B,T,2) easier to work with
    k = keys.clone()
    r = radii.clone() if radii is not None else None

    def scale_axis(axis: int, s: torch.Tensor):
        s_ = s.view(B, 1)
        p[:, :, axis] = (p[:, :, axis] - 0.5) * s_ + 0.5
        k[:, :, axis] = (k[:, :, axis] - 0.5) * s_ + 0.5
        if r is not None:
            r[:, :, axis] = r[:, :, axis] * s_

    # 1-2. axis scales
    scale_axis(1, _u(B, 0.75, 1.0, dev))
    scale_axis(0, _u(B, 0.85, 1.0, dev))

    # 3. shear
    sxy = _u(B, -0.05, 0.05, dev).view(B, 1)
    syx = _u(B, -0.05, 0.05, dev).view(B, 1)
    for t in (p, k):
        t[:, :, 0] = t[:, :, 0] + sxy * (t[:, :, 1] - 0.5)
        t[:, :, 1] = t[:, :, 1] + syx * (t[:, :, 0] - 0.5)

    # 4. flips
    for axis in (0, 1):
        f = (torch.rand(B, 1, device=dev) < 0.5).float()
        p[:, :, axis] = f * (1.0 - p[:, :, axis]) + (1 - f) * p[:, :, axis]
        k[:, :, axis] = f * (1.0 - k[:, :, axis]) + (1 - f) * k[:, :, axis]

    # 5. rotation about the trajectory centroid, rejected if it leaves the square
    theta = torch.rand(B, device=dev) * (2 * math.pi)
    cos, sin = torch.cos(theta).view(B, 1), torch.sin(theta).view(B, 1)
    c = p.mean(dim=1, keepdim=True)                       # (B,1,2)

    def rot(t):
        d = t - c
        x = d[:, :, 0] * cos - d[:, :, 1] * sin
        y = d[:, :, 0] * sin + d[:, :, 1] * cos
        return torch.stack([x, y], dim=-1) + c

    pr, kr = rot(p), rot(k)
    inside = torch.ones(B, dtype=torch.bool, device=dev)
    for t in (pr, kr):
        inside &= (t >= 0.0).all(dim=(1, 2)) & (t <= 1.0).all(dim=(1, 2))
    m = inside.view(B, 1, 1)
    p = torch.where(m, pr, p)
    k = torch.where(m, kr, k)
    if r is not None:
        # a rotated key is no longer axis-aligned; use the enclosing half-extent
        rr = torch.stack([
            (r[:, :, 0] * cos.abs() + r[:, :, 1] * sin.abs()),
            (r[:, :, 0] * sin.abs() + r[:, :, 1] * cos.abs()),
        ], dim=-1)
        r = torch.where(m, rr, r)

    # 6. translation: slide the combined bounding box to a random legal origin
    both = torch.cat([p, k], dim=1)
    lo = both.amin(dim=1)                                 # (B,2)
    hi = both.amax(dim=1)
    span = (hi - lo).clamp(max=1.0)
    free = (1.0 - span).clamp(min=0.0)
    origin = torch.rand(B, 2, device=dev) * free
    shift = (origin - lo).unsqueeze(1)
    p = p + shift
    k = k + shift

    # 7. time reversal -- the caller must reverse the label to match
    rev = torch.rand(B, device=dev) < p_reverse
    p = torch.where(rev.view(B, 1, 1), p.flip(1), p)

    return p.transpose(1, 2).contiguous(), k, r, rev


if __name__ == "__main__":
    from swipe.layout import Layout
    lay = Layout.from_json("data/hf/swipe-5/layouts/qwerty.json", aspect=2.478)
    keys = torch.tensor([lay.cx, lay.cy], dtype=torch.float32).T.unsqueeze(0).repeat(512, 1, 1)
    xy = torch.rand(512, 2, 64) * 0.8 + 0.1
    a, k, _, rev = co_augment(xy, keys)
    print("traj range", a.min().item(), a.max().item())
    print("keys range", k.min().item(), k.max().item())
    print("reversed fraction", rev.float().mean().item(), "(target 0.10)")
    assert a.min() >= -1e-4 and a.max() <= 1 + 1e-4, "trajectory left the unit square"
    assert k.min() >= -1e-4 and k.max() <= 1 + 1e-4, "keys left the unit square"
    print("OK: everything stays inside [0,1]^2")
