"""Keyboard layouts in normalized coordinates, the format the model consumes.

The dataset gives key positions as cx/cy/rx/ry in [0, 1] of the keyboard area,
and gives trace points in the same space. Keeping the model in that space -- and
feeding the key positions in as *features* rather than baking them into weights
-- is what lets one encoder serve any layout, including this app's, whose rows
have three different key widths.

Because x and y are each normalized independently, [0,1]^2 is not isotropic: a
step of 0.01 in x is a different physical distance than 0.01 in y. Anything
measuring distance therefore works in `aspect` space, where x is scaled by
canvas_width / canvas_height.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np

LETTERS = "abcdefghijklmnopqrstuvwxyz"
ALPHABET = 26


class Layout:
    """Key centres and radii in [0,1]^2, plus an aspect ratio for distances."""

    __slots__ = ("cx", "cy", "rx", "ry", "aspect", "name")

    def __init__(self, cx, cy, rx, ry, aspect: float, name: str = ""):
        self.cx = np.asarray(cx, dtype=np.float32)
        self.cy = np.asarray(cy, dtype=np.float32)
        self.rx = np.asarray(rx, dtype=np.float32)
        self.ry = np.asarray(ry, dtype=np.float32)
        self.aspect = float(aspect)
        self.name = name

    @property
    def unit(self) -> float:
        """Mean key width in aspect space -- the unit tolerances are measured in."""
        return float((2.0 * self.rx).mean() * self.aspect)

    def centers_aspect(self) -> np.ndarray:
        """(26, 2) key centres in aspect space."""
        return np.stack([self.cx * self.aspect, self.cy], axis=1)

    def to_aspect(self, xy: np.ndarray) -> np.ndarray:
        """Map normalized trace points into aspect space."""
        out = np.asarray(xy, dtype=np.float32).copy()
        out[:, 0] *= self.aspect
        return out

    @classmethod
    def from_json(cls, path: str | Path, aspect: float) -> "Layout":
        spec = json.loads(Path(path).read_text())
        cx = np.zeros(ALPHABET, np.float32)
        cy = np.zeros(ALPHABET, np.float32)
        rx = np.zeros(ALPHABET, np.float32)
        ry = np.zeros(ALPHABET, np.float32)
        seen = np.zeros(ALPHABET, bool)
        for k in spec["keys"]:
            ch = k["letter"]
            if len(ch) != 1 or ch not in LETTERS:
                continue
            i = LETTERS.index(ch)
            cx[i], cy[i], rx[i], ry[i] = k["cx"], k["cy"], k["rx"], k["ry"]
            seen[i] = True
        if not seen.all():
            missing = [LETTERS[i] for i in np.flatnonzero(~seen)]
            raise ValueError(f"{path}: layout is missing {missing}")
        return cls(cx, cy, rx, ry, aspect, spec.get("name", Path(path).stem))

    @classmethod
    def from_app(cls, width_px: float = 1080.0, row_height_px: float = 135.0,
                 density: float = 2.8125) -> "Layout":
        """This app's own geometry, converted into the same normalized space."""
        from .keymap import build
        km = build(width_px=width_px, row_height_px=row_height_px, density=density)
        total_h = row_height_px * 3.0
        return cls(
            cx=km.cx / width_px,
            cy=km.cy / total_h,
            rx=(km.kw / 2.0) / width_px,
            ry=(km.kh / 2.0) / total_h,
            aspect=width_px / total_h,
            name="composekeyboard",
        )


if __name__ == "__main__":
    import sys
    p = Path("data/hf/swipe-5/layouts/qwerty.json")
    if p.exists():
        futo = Layout.from_json(p, aspect=422 / 170.3125)
        print(f"futo qwerty : unit={futo.unit:.4f} aspect={futo.aspect:.3f}")
        for r, row in enumerate(["qwertyuiop", "asdfghjkl", "zxcvbnm"]):
            i = LETTERS.index(row[0])
            print(f"   row{r+1} y={futo.cy[i]:.4f} keyw={2*futo.rx[i]:.4f} x0={futo.cx[i]:.4f}")
    app = Layout.from_app()
    print(f"app qwerty  : unit={app.unit:.4f} aspect={app.aspect:.3f}")
    for r, row in enumerate(["qwertyuiop", "asdfghjkl", "zxcvbnm"]):
        i = LETTERS.index(row[0])
        print(f"   row{r+1} y={app.cy[i]:.4f} keyw={2*app.rx[i]:.4f} x0={app.cx[i]:.4f}")
