"""Trie-constrained CTC prefix beam search.

Two details carry most of the accuracy:

* **CTC blank semantics.** Extending a prefix by the character it already ends
  with is only allowed from the blank-ending mass. That is exactly what makes
  'putt' distinguishable from 'put': the model has to emit a blank between the
  two t's, which it will only do if the finger actually dwelt there. Geometry
  alone cannot represent this distinction at all.

* **Length-aware pruning.** Raw CTC log-probability shrinks monotonically with
  depth, so a plain top-k beam quietly strangles long words in favour of short
  prefixes. Pruning on s_ctc / max(d,1)^gamma + beta*d compensates.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

BLANK = 26
NEG_INF = -1e30

# Pruning and scoring constants; retuned on dev by tools/tune_scoring.py.
GAMMA_PRUNE = 0.2582
BETA_PRUNE = 0.9722
GAMMA_SCORE = 0.3499
LAMBDA_FREQ = 0.0351
BETA_LEN = 0.6065


class TrieNode:
    __slots__ = ("children", "word", "score")

    def __init__(self):
        self.children: dict[int, "TrieNode"] = {}
        self.word: str | None = None
        self.score: int = 0


def build_trie(words, scores) -> TrieNode:
    root = TrieNode()
    for w, sc in zip(words, scores):
        node = root
        ok = True
        for c in w:
            if not ("a" <= c <= "z"):
                continue
            node = node.children.setdefault(ord(c) - 97, TrieNode())
        if ok and node is not root:
            # keep the highest-scoring spelling when two normalize to one path
            if node.word is None or sc > node.score:
                node.word, node.score = w, sc
    return root


def _logaddexp(a: float, b: float) -> float:
    if a < b:
        a, b = b, a
    if b <= NEG_INF / 2:
        return a
    return a + math.log1p(math.exp(b - a))


def beam_search(log_probs: np.ndarray, root: TrieNode, beam_width: int = 100,
                max_results: int = 10, return_candidates: bool = False,
                gamma_prune: float = GAMMA_PRUNE, beta_prune: float = BETA_PRUNE,
                gamma_score: float = GAMMA_SCORE, lambda_freq: float = LAMBDA_FREQ,
                beta_len: float = BETA_LEN):
    """log_probs: (T, 27) log emissions, blank last. Returns [(word, score), ...]."""
    T = log_probs.shape[0]
    # beam entry: key -> [node, depth, last_char, log p_blank, log p_nonblank]
    beams = {(id(root),): [root, 0, -1, 0.0, NEG_INF]}

    for t in range(T):
        lp = log_probs[t]
        lp_blank = float(lp[BLANK])
        nxt: dict[tuple, list] = {}

        def add(key, node, depth, last, pb, pnb):
            e = nxt.get(key)
            if e is None:
                nxt[key] = [node, depth, last, pb, pnb]
            else:
                e[3] = _logaddexp(e[3], pb)
                e[4] = _logaddexp(e[4], pnb)

        for key, (node, depth, last, pb, pnb) in beams.items():
            total = _logaddexp(pb, pnb)

            # emit blank: prefix unchanged, mass moves to the blank-ending slot
            add(key, node, depth, last, total + lp_blank, NEG_INF)

            # repeat the final character without an intervening blank
            if last >= 0:
                add(key, node, depth, last, NEG_INF, pnb + float(lp[last]))

            for ch, child in node.children.items():
                p = float(lp[ch])
                # a repeat of the last character may only grow out of blank mass
                src = pb if ch == last else total
                if src <= NEG_INF / 2:
                    continue
                add(key + (ch,), child, depth + 1, ch, NEG_INF, src + p)

        if len(nxt) > beam_width:
            def prune_key(item):
                _, (node, depth, last, pb, pnb) = item
                s = _logaddexp(pb, pnb)
                return s / max(depth, 1) ** gamma_prune + beta_prune * depth
            beams = dict(sorted(nxt.items(), key=prune_key, reverse=True)[:beam_width])
        else:
            beams = nxt

    cands = candidates_from(beams)
    if return_candidates:
        return cands
    return rank(cands, max_results, gamma_score, lambda_freq, beta_len)


def candidates_from(beams):
    """Surviving terminal beams as (word, ctc_logprob, length, freq)."""
    out = []
    for node, depth, last, pb, pnb in beams.values():
        if node.word is None:
            continue
        out.append((node.word, _logaddexp(pb, pnb), len(node.word), node.score))
    return out


def rank(cands, max_results=10, gamma_score=GAMMA_SCORE,
         lambda_freq=LAMBDA_FREQ, beta_len=BETA_LEN):
    """Apply the scoring formula. Separated so tuning can reuse cached beams."""
    scored = [(w, c / (L ** gamma_score) + lambda_freq * f + beta_len * L)
              for w, c, L, f in cands]
    scored.sort(key=lambda x: -x[1])
    return scored[:max_results]
