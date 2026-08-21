"""Does the encoder transfer to *this app's* keyboard?

The model is trained on the dataset's QWERTY, whose three rows all use one key
width. This app's rows do not: row 2 is inset by 8dp and row 3 spends 1.35
weight units on Shift and Backspace, so its three rows have three different key
widths. The paper's claim is that co-augmentation makes the encoder read the
layout it is handed rather than the one it trained on -- this checks that claim
on the geometry that actually ships.

Gestures are synthetic here (no real corpus exists for this layout), so the
absolute numbers are optimistic for both decoders. The comparison is still fair:
both see identical traces.
"""
import argparse, sys
import numpy as np, torch
sys.path.insert(0, ".")
from swipe.baseline import decode as geo_decode
from swipe.beam import build_trie, beam_search
from swipe.evaluate import keymap_from_layout, trace_to_pixels
from swipe.keymap import build as build_keymap
from swipe.layout import Layout
from swipe.lexicon import Lexicon
from swipe.model import SwipeEncoder
from swipe.synth import synth_gesture

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="runs/encoder/best.pt")
ap.add_argument("--n", type=int, default=1500)
ap.add_argument("--beam", type=int, default=100)
A = ap.parse_args()

lx = Lexicon()
TRIE = build_trie(lx.words, lx.scores)
ck = torch.load(A.ckpt, map_location="cuda", weights_only=False)
m = SwipeEncoder().cuda().eval()
m.load_state_dict({k.replace("_orig_mod.", ""): v for k, v in ck["model"].items()})

LAYOUTS = {
    "dataset qwerty (in-domain)": Layout.from_json(
        "data/hf/swipe-5/layouts/qwerty.json", aspect=422 / 170.3125),
    "this app's keyboard": Layout.from_app(),
    "app, tall keys (height x1.4)": Layout.from_app(row_height_px=189.0),
    "app, landscape-ish (wide)": Layout.from_app(width_px=1920.0, row_height_px=135.0),
}

print(f"checkpoint step {ck.get('step')}, {A.n} synthetic gestures per layout\n")
print(f"{'layout':>30s} {'geo top1':>9s} {'nn top1':>8s} {'nn top3':>8s} {'delta':>7s}")

for name, lay in LAYOUTS.items():
    # a pixel-space keymap consistent with this layout, for the synthesizer
    km = keymap_from_layout(lay)
    rng = np.random.default_rng(11)
    p = np.array(lx.scores, float); p /= p.sum()
    idx = rng.choice(len(lx), A.n, p=p)

    traces, times, targets = [], [], []
    for i in idx:
        w = lx.words[i]
        g = synth_gesture(w, km, rng, sloppy=0.30)
        if g is None or g[1][-1] <= 0:
            continue
        traces.append(g[0].astype(np.float64))
        times.append(g[1].astype(np.float64))
        targets.append(w)

    # geometric baseline reads pixels directly
    geo = sum(bool(r) and lx.words[r[0]] == t
              for r, t in ((geo_decode(xy, km, lx, 1), t) for xy, t in zip(traces, targets)))

    # neural path: normalize into the layout's unit box, hand it the key centres
    keys = torch.tensor(np.stack([lay.cx, lay.cy], 1), dtype=torch.float32, device="cuda")
    box_w = km.width; box_h = km.height
    T = 64
    batch = np.empty((len(traces), 2, T), np.float32)
    for j, (xy, ts) in enumerate(zip(traces, times)):
        # uniform in *time*, matching preprocess.py -- resampling by index here
        # would silently feed the model a different distribution than training
        grid = np.linspace(0.0, ts[-1], T)
        batch[j, 0] = np.interp(grid, ts, xy[:, 0]) / box_w
        batch[j, 1] = np.interp(grid, ts, xy[:, 1]) / box_h
    with torch.no_grad():
        lp_all = []
        for s in range(0, len(batch), 2048):
            xb = torch.from_numpy(batch[s:s+2048]).cuda()
            with torch.autocast("cuda", dtype=torch.bfloat16):
                lp, _ = m(xb, keys.unsqueeze(0).expand(len(xb), -1, -1))
            lp_all.append(lp.float().cpu().numpy())
    lp_all = np.concatenate(lp_all)

    n1 = n3 = 0
    for j, t in enumerate(targets):
        r = [w for w, _ in beam_search(lp_all[j], TRIE, beam_width=A.beam, max_results=3)]
        n1 += bool(r) and r[0] == t
        n3 += t in r
    n = len(targets)
    print(f"{name:>30s} {geo/n:8.1%} {n1/n:8.1%} {n3/n:8.1%} {(n1-geo)/n:+7.1%}")
