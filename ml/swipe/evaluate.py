"""Head-to-head evaluation on real swipes: geometric baseline vs neural encoder.

Both decoders read the same layout, the same lexicon and the same traces, so the
comparison is like-for-like. Two lexicon settings are reported: the word list the
app actually ships, and that list plus the evaluation vocabulary -- the second
isolates spatial decoding from out-of-vocabulary coverage, as the paper does.
"""
from __future__ import annotations

import numpy as np

from .keymap import KeyMap
from .layout import Layout
from .lexicon import Lexicon

SCALE = 1000.0          # arbitrary "pixels"; only ratios matter to the decoder


def keymap_from_layout(lay: Layout) -> KeyMap:
    """A pixel-space KeyMap for the geometric baseline, from a normalized layout."""
    a = lay.aspect
    return KeyMap(
        cx=(lay.cx * a * SCALE).astype(np.float64),
        cy=(lay.cy * SCALE).astype(np.float64),
        kw=(2 * lay.rx * a * SCALE).astype(np.float64),
        kh=(2 * lay.ry * SCALE).astype(np.float64),
        width=a * SCALE, height=SCALE,
    )


def trace_to_pixels(xy: np.ndarray, lay: Layout) -> np.ndarray:
    out = xy.astype(np.float64).copy()
    out[:, 0] *= lay.aspect * SCALE
    out[:, 1] *= SCALE
    return out


def normalize_word(w: str) -> str:
    return "".join(c for c in w.lower() if "a" <= c <= "z")


def extended_lexicon(lx: Lexicon, targets, base_score: int = 120) -> Lexicon:
    """Add evaluation targets missing from the shipped list, at a neutral prior."""
    have = set(lx.words)
    new = sorted({w for w in targets if w and w not in have})
    for w in new:
        k = np.array([ord(c) - 97 for c in w], dtype=np.int64)
        lx.words.append(w)
        lx.scores.append(base_score)
        lx.keys.append(k)
        lx.buckets[int(k[0])].append(len(lx.words) - 1)
    lx.index = {w: i for i, w in enumerate(lx.words)}
    return lx
