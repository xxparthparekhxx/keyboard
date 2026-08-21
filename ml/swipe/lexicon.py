"""The shipped word list, in the same buckets the app uses."""
from __future__ import annotations
import numpy as np
from pathlib import Path

ASSET = Path(__file__).resolve().parents[2] / "app/src/main/assets/swipe_words.txt"
MAX_WORD_LENGTH = 22


class Lexicon:
    def __init__(self, path: str | Path = ASSET):
        self.words: list[str] = []
        self.scores: list[int] = []
        self.keys: list[np.ndarray] = []
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.rstrip("\n")
                if not line or line[0] == "#":
                    continue
                tab = line.find("\t")
                if tab <= 0:
                    continue
                w = line[:tab]
                try:
                    s = int(line[tab + 1:].strip())
                except ValueError:
                    continue
                k = [ord(c) - 97 for c in w if "a" <= c <= "z"]
                if not (1 <= len(k) <= MAX_WORD_LENGTH):
                    continue
                self.words.append(w)
                self.scores.append(s)
                self.keys.append(np.array(k, dtype=np.int64))

        self.buckets: list[list[int]] = [[] for _ in range(26)]
        for i, k in enumerate(self.keys):
            self.buckets[int(k[0])].append(i)
        self.index = {w: i for i, w in enumerate(self.words)}

    def __len__(self):
        return len(self.words)


if __name__ == "__main__":
    lx = Lexicon()
    print(f"{len(lx)} words; bucket sizes min={min(map(len,lx.buckets))} "
          f"max={max(map(len,lx.buckets))} mean={sum(map(len,lx.buckets))/26:.0f}")
    print("score range", min(lx.scores), "-", max(lx.scores))
