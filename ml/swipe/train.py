"""Encoder training: CTC + emission-count penalty, under co-augmentation."""
from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F

from .augment import co_augment
from .data import Split
from .layout import Layout
from .model import SwipeEncoder, T_OUT

BLANK = 26


def layout_tensor(lay: Layout, device) -> tuple[torch.Tensor, torch.Tensor]:
    keys = torch.tensor(np.stack([lay.cx, lay.cy], 1), dtype=torch.float32, device=device)
    radii = torch.tensor(np.stack([lay.rx, lay.ry], 1), dtype=torch.float32, device=device)
    return keys, radii


def ctc_and_emit(log_probs, gate, labels, lengths, emit_weight: float):
    """log_probs: (B,T,C). Returns (loss, ctc, emit)."""
    lp = log_probs.transpose(0, 1)                       # (T,B,C) as CTC wants
    T, B, _ = lp.shape
    input_lengths = torch.full((B,), T, dtype=torch.long, device=lp.device)
    ctc = F.ctc_loss(lp.float(), labels, input_lengths, lengths,
                     blank=BLANK, reduction="mean", zero_infinity=True)
    # one-sided: penalise under-emission only, leave over-emission to CTC
    deficit = (lengths.float() - gate.sum(dim=1)).clamp(min=0.0)
    emit = (deficit ** 2).mean()
    return ctc + emit_weight * emit, ctc.detach(), emit.detach()


@torch.no_grad()
def greedy_word_acc(model, split: Split, keys, batch: int = 2048, limit: int = 20000):
    """Lexicon-free greedy CTC accuracy -- a cheap proxy to watch during training."""
    model.eval()
    n = min(len(split), limit)
    correct = 0
    for s in range(0, n, batch):
        idx = torch.arange(s, min(s + batch, n), device=split.device)
        xy, labels, lengths = split.batch(idx)
        k = keys.unsqueeze(0).expand(len(idx), -1, -1)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            lp, _ = model(xy, k)
        best = lp.float().argmax(-1)                      # (B,T)
        prev = torch.full_like(best[:, :1], -1)
        seq = torch.cat([prev, best], 1)
        keepm = (seq[:, 1:] != seq[:, :-1]) & (best != BLANK)
        for b in range(len(idx)):
            pred = best[b][keepm[b]].tolist()
            tgt = labels[b][: lengths[b]].tolist()
            correct += int(pred == tgt)
    model.train()
    return correct / n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=120)
    ap.add_argument("--batch", type=int, default=1024)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--lr-min", type=float, default=2e-5)
    ap.add_argument("--wd", type=float, default=1e-4)
    ap.add_argument("--emit-weight", type=float, default=0.05)
    ap.add_argument("--warmup", type=float, default=0.05)
    ap.add_argument("--dropout", type=float, default=0.1)
    ap.add_argument("--no-augment", action="store_true")
    ap.add_argument("--out", default="runs/encoder")
    ap.add_argument("--max-steps", type=int, default=0)
    ap.add_argument("--no-compile", action="store_true")
    args = ap.parse_args()

    dev = "cuda"
    torch.backends.cudnn.benchmark = True
    torch.backends.cuda.matmul.allow_tf32 = True
    torch.backends.cudnn.allow_tf32 = True

    train = Split("data/packed/train", dev)
    val = Split("data/packed/dev", dev)
    lay = Layout.from_json("data/hf/swipe-5/layouts/qwerty.json", aspect=2.478)
    keys, radii = layout_tensor(lay, dev)

    model = SwipeEncoder(dropout=args.dropout).to(dev)
    n_par = sum(p.numel() for p in model.parameters())
    # ~2x on this GPU; the model is small enough to be kernel-launch bound eager.
    net = torch.compile(model) if not args.no_compile else model

    decay, no_decay = [], []
    for name, p in model.named_parameters():
        (no_decay if p.ndim <= 1 else decay).append(p)
    opt = torch.optim.AdamW(
        [{"params": decay, "weight_decay": args.wd},
         {"params": no_decay, "weight_decay": 0.0}],
        lr=args.lr, betas=(0.9, 0.999))

    steps_per_epoch = len(train) // args.batch
    total = args.max_steps or steps_per_epoch * args.epochs
    warm = int(total * args.warmup)

    def lr_at(step):
        if step < warm:
            return args.lr * step / max(warm, 1)
        t = (step - warm) / max(total - warm, 1)
        return args.lr_min + 0.5 * (args.lr - args.lr_min) * (1 + math.cos(math.pi * t))

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    (out / "args.json").write_text(json.dumps(vars(args), indent=2))
    print(f"params {n_par:,} | train {len(train):,} | val {len(val):,} | "
          f"{steps_per_epoch} steps/epoch | {total:,} total steps")

    step = 0
    best = 0.0
    t0 = time.time()
    hist = []
    while step < total:
        perm = torch.randperm(len(train), device=dev)
        for s in range(steps_per_epoch):
            if step >= total:
                break
            idx = perm[s * args.batch:(s + 1) * args.batch]
            xy, labels, lengths = train.batch(idx)
            B = xy.shape[0]
            k = keys.unsqueeze(0).expand(B, -1, -1)

            if args.no_augment:
                kb = k
            else:
                r = radii.unsqueeze(0).expand(B, -1, -1)
                xy, kb, _, rev = co_augment(xy, k, r)
                if rev.any():
                    ar = torch.arange(labels.shape[1], device=dev).unsqueeze(0)
                    ridx = (lengths.unsqueeze(1) - 1 - ar).clamp(min=0)
                    labels = torch.where(rev.unsqueeze(1),
                                         labels.gather(1, ridx), labels)

            for g in opt.param_groups:
                g["lr"] = lr_at(step)

            with torch.autocast("cuda", dtype=torch.bfloat16):
                lp, gate = net(xy, kb)
            loss, ctc, emit = ctc_and_emit(lp, gate.float(), labels, lengths, args.emit_weight)

            opt.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            step += 1

            if step % 250 == 0:
                el = time.time() - t0
                print(f"step {step:6d}/{total}  ctc {ctc.item():.4f}  emit {emit.item():.3f}  "
                      f"lr {lr_at(step):.2e}  {step/el:.1f} it/s  {el/60:.1f} min", flush=True)

            if step % 2500 == 0 or step == total:
                acc = greedy_word_acc(net, val, keys)
                hist.append({"step": step, "greedy_acc": acc, "ctc": ctc.item()})
                star = ""
                if acc > best:
                    best = acc
                    torch.save({"model": model.state_dict(), "step": step,
                                "greedy_acc": acc, "args": vars(args)}, out / "best.pt")
                    star = "  *"
                print(f"  [val] step {step}  greedy word acc {acc:.4f}{star}", flush=True)
                (out / "history.json").write_text(json.dumps(hist, indent=2))

    torch.save({"model": model.state_dict(), "step": step, "args": vars(args)}, out / "last.pt")
    print(f"done in {(time.time()-t0)/60:.1f} min; best greedy acc {best:.4f}")


if __name__ == "__main__":
    main()
