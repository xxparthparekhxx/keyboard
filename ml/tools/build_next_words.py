"""Build the shipped next-word table (app/src/main/assets/next_words.txt).

Counts which word follows which in English web text, so next-word
prediction works before the keyboard has learned anything from its user.
On-device learning (NextWordPredictor) then layers the user's own pairs on
top of this table.

Source: C4 (allenai/c4, English split), ODC-BY 1.0, pinned to an immutable
revision. Fetch the shard first:

    mkdir -p data/c4 && curl -L -o data/c4/c4-train.00000-of-01024.json.gz \\
      https://huggingface.co/datasets/allenai/c4/resolve/1588ec454efa1a09f29cd18ddd04fe05fc8653a2/en/c4-train.00000-of-01024.json.gz

then, from ml/:

    python tools/build_next_words.py --source data/c4/c4-train.00000-of-01024.json.gz \\
      --out ../app/src/main/assets/next_words.txt

Tokenization mirrors how the app tracks words (WordContext, the service's
trackInput): a word is letters and apostrophes, words separated by spaces
continue a context, and anything else -- punctuation, digits, line breaks --
ends it. Pairs are kept only when both words are in the shipped lexicon
(plus "i" and "a", which the swipe lexicon leaves out), so the table never
suggests a word the keyboard could not otherwise produce.

Output, one context per line, sorted by how common the context word is:

    prev<TAB>next,score<TAB>next,score...

`prev` is lower-case. `next` is in its usual casing ("I", "York"). `score` is
P(next | prev) in thousandths, 1..999.
"""
import argparse
import gzip
import json
import multiprocessing as mp
import re
import sys
from pathlib import Path

import numpy as np

ap = argparse.ArgumentParser()
ap.add_argument("--source", nargs="+", required=True,
                help="C4 .json.gz shards, or plain .txt files")
ap.add_argument("--lexicon", default="../app/src/main/assets/swipe_words.txt")
ap.add_argument("--out", required=True)
ap.add_argument("--contexts", type=int, default=20_000,
                help="how many preceding words get a row (most frequent first)")
ap.add_argument("--followers", type=int, default=6, help="followers kept per context")
ap.add_argument("--min-count", type=int, default=5, help="pair sightings needed")
ap.add_argument("--min-score", type=int, default=5, help="P(next|prev) in thousandths")
ap.add_argument("--procs", type=int, default=max(1, mp.cpu_count() - 2))
ap.add_argument("--max-docs", type=int, default=0, help="stop after this many documents")
A = ap.parse_args()

WORD = re.compile(r"[a-z']+")
# Anything that is not a letter, apostrophe or space ends a context.
BREAK = re.compile(r"[^A-Za-z' ]+")
BATCH = 2_000


def load_vocab(path: str) -> list[str]:
    words = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            word = line.split("\t", 1)[0].strip()
            if word and WORD.fullmatch(word) and any(c != "'" for c in word):
                words.append(word)
    for extra in ("i", "a"):
        if extra not in words:
            words.append(extra)
    return words


VOCAB: dict[str, int] = {}


def init_worker(vocab: list[str]) -> None:
    global VOCAB
    VOCAB = {w: i for i, w in enumerate(vocab)}


def count_batch(lines: list[str]):
    """Pair codes (prev * V + next) with counts, plus per-word casing tallies."""
    v = len(VOCAB)
    codes = []
    seen = np.zeros(v, dtype=np.int64)      # occurrences not first in their context
    capital = np.zeros(v, dtype=np.int64)   # ... of which were capitalized
    for line in lines:
        text = json.loads(line)["text"] if line.startswith("{") else line
        text = text.replace("’", "'")
        for segment in BREAK.split(text):
            prev = -1
            first = True
            for token in segment.split():
                token = token.strip("'")
                if not token:
                    prev = -1
                    continue
                wid = VOCAB.get(token.lower(), -1)
                if wid >= 0:
                    if prev >= 0:
                        codes.append(prev * v + wid)
                    if not first:
                        seen[wid] += 1
                        if token[0].isupper():
                            capital[wid] += 1
                prev = wid
                first = False
    if codes:
        keys, counts = np.unique(np.asarray(codes, dtype=np.int64), return_counts=True)
    else:
        keys, counts = np.zeros(0, np.int64), np.zeros(0, np.int64)
    return keys, counts.astype(np.int64), seen, capital


def read_batches(paths: list[str], max_docs: int):
    batch, docs = [], 0
    for path in paths:
        opener = gzip.open if path.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as f:
            for line in f:
                batch.append(line)
                docs += 1
                if len(batch) >= BATCH:
                    yield batch
                    batch = []
                if max_docs and docs >= max_docs:
                    if batch:
                        yield batch
                    return
    if batch:
        yield batch


def merge(parts_keys, parts_counts):
    keys = np.concatenate(parts_keys)
    counts = np.concatenate(parts_counts)
    uniq, inverse = np.unique(keys, return_inverse=True)
    return uniq, np.bincount(inverse, weights=counts).astype(np.int64)


def main() -> None:
    vocab = load_vocab(A.lexicon)
    v = len(vocab)
    print(f"vocabulary: {v:,} words", file=sys.stderr)

    keys = np.zeros(0, np.int64)
    counts = np.zeros(0, np.int64)
    seen = np.zeros(v, np.int64)
    capital = np.zeros(v, np.int64)
    pending_k, pending_c = [], []
    batches = 0
    with mp.Pool(A.procs, initializer=init_worker, initargs=(vocab,)) as pool:
        for k, c, s, cap in pool.imap_unordered(count_batch, read_batches(A.source, A.max_docs)):
            pending_k.append(k)
            pending_c.append(c)
            seen += s
            capital += cap
            batches += 1
            if len(pending_k) >= 64:
                keys, counts = merge([keys] + pending_k, [counts] + pending_c)
                pending_k, pending_c = [], []
                print(f"  {batches * BATCH:,} docs, {len(keys):,} distinct pairs", file=sys.stderr)
    keys, counts = merge([keys] + pending_k, [counts] + pending_c)

    prev_ids = keys // v
    next_ids = keys % v
    # How often each word was followed by an in-vocabulary word: the
    # denominator for P(next | prev).
    as_prev = np.bincount(prev_ids, weights=counts, minlength=v).astype(np.int64)
    contexts = np.argsort(-as_prev, kind="stable")[: A.contexts]
    contexts = contexts[as_prev[contexts] > 0]

    def display(wid: int) -> str:
        word = vocab[wid]
        # Usual casing, judged where a capital is not forced by position.
        if seen[wid] >= 20 and capital[wid] > 0.6 * seen[wid]:
            return word[0].upper() + word[1:]
        return word

    order = np.argsort(prev_ids, kind="stable")
    prev_sorted = prev_ids[order]
    starts = np.searchsorted(prev_sorted, np.arange(v), side="left")
    ends = np.searchsorted(prev_sorted, np.arange(v), side="right")

    rows = 0
    pairs = 0
    out = Path(A.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("# next-word table: prev<TAB>next,score... (score = P(next|prev) in 1/1000)\n")
        f.write("# Built by ml/tools/build_next_words.py from C4 (allenai/c4, ODC-BY 1.0)\n")
        for pid in contexts:
            idx = order[starts[pid]:ends[pid]]
            if len(idx) == 0:
                continue
            idx = idx[np.argsort(-counts[idx], kind="stable")]
            total = as_prev[pid]
            cells = []
            for i in idx[: A.followers * 3]:
                c = int(counts[i])
                score = round(1000 * c / total)
                if c < A.min_count or score < A.min_score:
                    break
                cells.append(f"{display(int(next_ids[i]))},{min(score, 999)}")
                if len(cells) == A.followers:
                    break
            if cells:
                f.write(vocab[pid] + "\t" + "\t".join(cells) + "\n")
                rows += 1
                pairs += len(cells)
    print(f"wrote {rows:,} contexts, {pairs:,} pairs, {out.stat().st_size / 1e6:.2f} MB -> {out}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
