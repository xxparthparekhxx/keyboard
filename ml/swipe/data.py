"""Packed splits, resident on the GPU.

The whole training set is 229 MB in fp16, so it lives on the device and batches
are gathered by index. No workers, no collate, no host->device copy per step --
the augmentation pipeline is already GPU-side, so nothing needs the CPU at all.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import torch

MAX_LABEL = 24


class Split:
    def __init__(self, root: str | Path, device="cuda", max_label: int = MAX_LABEL):
        root = Path(root)
        traces = np.load(root / "traces.npy")               # (N, T, 2) fp16
        labels = np.load(root / "labels.npy")               # flat uint8
        offsets = np.load(root / "offsets.npy")             # (N+1,)
        lengths = np.diff(offsets)

        keep = (lengths >= 2) & (lengths <= max_label)
        idx = np.flatnonzero(keep)

        padded = np.zeros((len(idx), max_label), dtype=np.int64)
        for j, i in enumerate(idx):
            padded[j, : lengths[i]] = labels[offsets[i]:offsets[i + 1]]

        self.xy = torch.from_numpy(
            np.ascontiguousarray(traces[idx].transpose(0, 2, 1))).to(device)   # (N,2,T) fp16
        self.labels = torch.from_numpy(padded).to(device)
        self.lengths = torch.from_numpy(lengths[idx].astype(np.int64)).to(device)
        self.words = [w for k, w in enumerate(
            (root / "words.txt").read_text(encoding="utf-8").split("\n")) if keep[k]]
        self.n = len(idx)
        self.device = device

    def __len__(self):
        return self.n

    def batch(self, index: torch.Tensor):
        return (self.xy[index].float(), self.labels[index], self.lengths[index])
