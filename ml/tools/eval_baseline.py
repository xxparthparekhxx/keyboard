"""Geometric (SHARK2-family) baseline on the FUTO test split."""
import sys, time
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, ".")
from swipe.baseline import decode
from swipe.evaluate import keymap_from_layout, trace_to_pixels, normalize_word, extended_lexicon
from swipe.layout import Layout
from swipe.lexicon import Lexicon

SPLIT = sys.argv[1] if len(sys.argv) > 1 else "test"
EXTEND = len(sys.argv) > 2 and sys.argv[2] == "extend"
LIMIT = int(sys.argv[3]) if len(sys.argv) > 3 else 0
LEXPATH = sys.argv[4] if len(sys.argv) > 4 else ""

lay = Layout.from_json("data/hf/swipe-5/layouts/qwerty.json", aspect=422/170.3125)
km = keymap_from_layout(lay)
traces = np.load(f"data/packed/{SPLIT}/traces.npy")
words = [normalize_word(w) for w in open(f"data/packed/{SPLIT}/words.txt").read().split("\n")]
keep = [i for i, w in enumerate(words) if len(w) >= 2]
if LIMIT:
    keep = keep[:LIMIT]

lx = Lexicon(LEXPATH) if LEXPATH else Lexicon()
if EXTEND:
    lx = extended_lexicon(lx, {words[i] for i in keep})

def work(i):
    xy = trace_to_pixels(traces[i].astype(np.float64), lay)
    res = decode(xy, km, lx, 10)
    return words[i], [lx.words[j] for j in res]

if __name__ == "__main__":
    t0 = time.time()
    with Pool(30) as p:
        out = p.map(work, keep, chunksize=64)
    n = len(out)
    t1 = sum(bool(g) and g[0] == w for w, g in out)
    t3 = sum(w in g[:3] for w, g in out)
    t10 = sum(w in g[:10] for w, g in out)
    none = sum(not g for _, g in out)
    base = LEXPATH.split("/")[-1] if LEXPATH else "app35k"
    tag = base + ("+eval" if EXTEND else "")
    print(f"BASELINE  {SPLIT}  lexicon={tag}  n={n:,}")
    print(f"  top-1 {t1/n:7.2%}   top-3 {t3/n:7.2%}   top-10 {t10/n:7.2%}   no-result {none/n:.2%}")
    print(f"  {time.time()-t0:.0f}s wall on 30 procs")
