"""Key geometry, reproducing the app's actual Compose layout.

Every row is a `Row(Arrangement.Center)` whose children carry `Modifier.weight`,
so keys stretch to fill the row and each row's key width is set by that row's
total weight -- not by a shared grid. Row 2 additionally gets an 8dp Spacer on
each side (alpha mode only), and row 3 spends 1.35 weight units on Shift and on
Backspace. The three rows therefore have three different key widths, and the
synthesizer has to honour that or it trains on a keyboard that does not exist.

Mirrors KeyboardData.kt / KeyboardScreen.kt.
"""

from __future__ import annotations

import numpy as np

ALPHABET = 26

# (weight, label-or-None). None marks a non-letter key that still consumes width.
ROWS: list[list[tuple[float, str | None]]] = [
    [(1.0, c) for c in "qwertyuiop"],
    [(1.0, c) for c in "asdfghjkl"],
    [(1.35, None)] + [(1.0, c) for c in "zxcvbnm"] + [(1.35, None)],
]

ROW_H_PADDING_DP = 2.0
ROW_V_PADDING_DP = 1.0

# Spacer(Modifier.width(8.dp)) either side of row 2, alpha mode only.
ROW_INSET_DP = [0.0, 8.0, 0.0]


class KeyMap:
    """Letter-key centres and sizes in pixels, plus the decoder's `unit`."""

    __slots__ = ("cx", "cy", "kw", "kh", "unit", "width", "height")

    def __init__(self, cx, cy, kw, kh, width, height):
        self.cx = cx          # (26,) x centres
        self.cy = cy          # (26,) y centres
        self.kw = kw          # (26,) per-key widths
        self.kh = kh          # (26,) per-key heights
        self.width = width
        self.height = height
        # SwipeKeyGeometry.keyWidth is the *mean* over placed letter keys.
        self.unit = float(kw.mean())

    def centers(self) -> np.ndarray:
        """(26, 2) array of key centres."""
        return np.stack([self.cx, self.cy], axis=1)

    def path_for(self, word: str) -> np.ndarray:
        """(L, 2) polyline through the word's key centres, letters only."""
        idx = [ord(c) - 97 for c in word if "a" <= c <= "z"]
        return self.centers()[idx]


def build(
    width_px: float = 1080.0,
    row_height_px: float = 135.0,
    density: float = 2.8125,
    top_px: float = 0.0,
) -> KeyMap:
    """Lay out the alphabet exactly the way Compose does.

    `density` is px-per-dp (450dpi / 160 = 2.8125 on the target device).
    """
    cx = np.zeros(ALPHABET, dtype=np.float64)
    cy = np.zeros(ALPHABET, dtype=np.float64)
    kw = np.zeros(ALPHABET, dtype=np.float64)
    kh = np.zeros(ALPHABET, dtype=np.float64)

    h_pad = ROW_H_PADDING_DP * density
    v_pad = ROW_V_PADDING_DP * density

    usable_w = width_px - 2.0 * h_pad
    key_h = row_height_px - 2.0 * v_pad

    for r, row in enumerate(ROWS):
        total_weight = sum(w for w, _ in row)
        row_top = top_px + r * row_height_px + v_pad
        y_center = row_top + key_h / 2.0

        inset = ROW_INSET_DP[r] * density
        row_w = usable_w - 2.0 * inset

        x = h_pad + inset
        for weight, label in row:
            w_px = row_w * (weight / total_weight)
            if label is not None:
                i = ord(label) - 97
                cx[i] = x + w_px / 2.0
                cy[i] = y_center
                kw[i] = w_px
                kh[i] = key_h
            x += w_px

    return KeyMap(cx, cy, kw, kh, width_px, top_px + len(ROWS) * row_height_px)


if __name__ == "__main__":
    km = build()
    print(f"unit (mean key width) = {km.unit:.2f}px   key height = {km.kh[0]:.2f}px")
    for row in ROWS:
        labels = [l for _, l in row if l]
        w = km.kw[ord(labels[0]) - 97]
        print(f"  {''.join(labels):12s} key width {w:7.2f}px  y={km.cy[ord(labels[0])-97]:7.2f}")
