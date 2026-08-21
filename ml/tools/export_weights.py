"""Export the encoder to a flat binary the Kotlin runtime reads directly.

BatchNorm is folded into the convolution that precedes it, so the on-device
forward pass has no normalization layers at all -- one less thing to reimplement
and one less pass over the activations.

Format: magic 'SWEN', version, count, then per tensor
  [name_len:i32][name:utf8][ndim:i32][dims:i32...][data:f32...]
All little-endian.
"""
import argparse, struct, sys
import numpy as np, torch
sys.path.insert(0, ".")
from swipe.model import SwipeEncoder, N_DCT

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="runs/encoder/best.pt")
ap.add_argument("--out", default="../app/src/main/assets/swipe_encoder.bin")
A = ap.parse_args()

ck = torch.load(A.ckpt, map_location="cpu", weights_only=False)
model = SwipeEncoder()
model.load_state_dict({k.replace("_orig_mod.", ""): v for k, v in ck["model"].items()})
model.eval()

T = {}

def put(name, t):
    T[name] = np.ascontiguousarray(t.detach().cpu().numpy().astype(np.float32))

def fold_bn(conv_w, conv_b, bn):
    """Fold BatchNorm(gamma,beta,mean,var) into the preceding conv."""
    g, b = bn.weight.detach(), bn.bias.detach()
    m, v = bn.running_mean.detach(), bn.running_var.detach()
    s = g / torch.sqrt(v + bn.eps)
    w = conv_w * s.view(-1, *([1] * (conv_w.dim() - 1)))
    bias = (conv_b if conv_b is not None else torch.zeros_like(m))
    return w, (bias - m) * s + b

# Savitzky-Golay kernels (already flipped for correlation in the module)
put("savgol", model.features.kern.squeeze(1))                 # (3, 7)

put("stem.w", model.stem.weight); put("stem.b", model.stem.bias)

for i, blk in enumerate(model.blocks):
    w, b = fold_bn(blk.dw.weight, blk.dw.bias, blk.bn)
    put(f"b{i}.dw.w", w); put(f"b{i}.dw.b", b)
    put(f"b{i}.pw1.w", blk.pw1.weight); put(f"b{i}.pw1.b", blk.pw1.bias)
    put(f"b{i}.grn.g", blk.grn.gamma.flatten()); put(f"b{i}.grn.b", blk.grn.beta.flatten())
    put(f"b{i}.pw2.w", blk.pw2.weight); put(f"b{i}.pw2.b", blk.pw2.bias)
    put(f"b{i}.se1.w", blk.se.fc1.weight); put(f"b{i}.se1.b", blk.se.fc1.bias)
    put(f"b{i}.se2.w", blk.se.fc2.weight); put(f"b{i}.se2.b", blk.se.fc2.bias)

w, b = fold_bn(model.adapter.weight, model.adapter.bias, model.adapter_bn)
put("adapter.w", w); put("adapter.b", b)
put("coeff.w", model.coeff.weight); put("coeff.b", model.coeff.bias)
put("gate.w", model.gate.weight); put("gate.b", model.gate.bias)

with open(A.out, "wb") as f:
    f.write(b"SWEN")
    f.write(struct.pack("<ii", 1, len(T)))
    for name, arr in T.items():
        nb = name.encode()
        f.write(struct.pack("<i", len(nb))); f.write(nb)
        f.write(struct.pack("<i", arr.ndim))
        f.write(struct.pack(f"<{arr.ndim}i", *arr.shape))
        f.write(arr.tobytes())

total = sum(a.size for a in T.values())
import os
print(f"wrote {A.out}: {len(T)} tensors, {total:,} floats, {os.path.getsize(A.out)/1e6:.2f} MB")

# ---- reference vectors so the Kotlin port can be checked against torch ----
torch.manual_seed(0)
xy = torch.rand(1, 2, 64)
keys = torch.rand(1, 26, 2)
with torch.no_grad():
    feats = model.features(xy)
    lp, gate = model(xy, keys)
np.savez("runs/reference.npz", xy=xy.numpy(), keys=keys.numpy(),
         feats=feats.numpy(), logp=lp.numpy(), gate=gate.numpy())
print("reference vectors -> runs/reference.npz")
print(f"  logp[0,0,:4] = {lp[0,0,:4].tolist()}")
print(f"  N_DCT={N_DCT}")
