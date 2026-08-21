"""Build the shipped word list.

Score is round(log2(count) * 10) clamped to 1..255 -- the same log-frequency
convention the app already used, so nothing downstream needs to change.

A bigger list cuts out-of-vocabulary misses but adds confusable candidates, so
the size is chosen by measuring end-to-end accuracy rather than by minimizing
OOV alone.
"""
import argparse, math, sys
from pathlib import Path
sys.path.insert(0, ".")

ap = argparse.ArgumentParser()
ap.add_argument("--source", default="data/lex/en_full.txt")
ap.add_argument("--keep-existing", default="../app/src/main/assets/swipe_words.txt")
ap.add_argument("--size", type=int, default=150_000)
ap.add_argument("--out", required=True)
A = ap.parse_args()

MAX_LEN = 22


def usable(w: str) -> bool:
    if not (2 <= len(w) <= MAX_LEN):
        return False
    if not all("a" <= c <= "z" or c in "'-" for c in w):
        return False
    return sum(1 for c in w if "a" <= c <= "z") >= 2


freq: dict[str, int] = {}
for line in open(A.source, encoding="utf-8", errors="ignore"):
    parts = line.split()
    if len(parts) != 2:
        continue
    w = parts[0].lower()
    if not usable(w):
        continue
    try:
        freq[w] = freq.get(w, 0) + int(parts[1])
    except ValueError:
        continue

ranked = sorted(freq.items(), key=lambda kv: -kv[1])[:A.size]
scored = {w: max(1, min(255, round(math.log2(max(c, 2)) * 10))) for w, c in ranked}

# Never drop a word the app already shipped: the user may have learned to rely
# on it, and the curated contractions are not in a subtitle frequency list.
kept = 0
existing = Path(A.keep_existing)
if existing.exists():
    for line in existing.read_text(encoding="utf-8").splitlines():
        if not line or line[0] == "#" or "\t" not in line:
            continue
        w, s = line.split("\t", 1)
        if not usable(w):
            continue
        if w not in scored:
            scored[w] = max(1, min(255, int(s.strip())))
            kept += 1

out = sorted(scored.items(), key=lambda kv: (-kv[1], kv[0]))
with open(A.out, "w", encoding="utf-8") as f:
    f.write(f"# swipe lexicon: {len(out)} words, score = round(log2(count)*10) clamped 1..255\n")
    for w, s in out:
        f.write(f"{w}\t{s}\n")
print(f"{A.out}: {len(out):,} words ({kept:,} carried over from the old list), "
      f"{Path(A.out).stat().st_size/1e6:.2f} MB")
