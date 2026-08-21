"""Neural encoder + trie beam search on the FUTO test split."""
import argparse, sys, time
from multiprocessing import Pool
import numpy as np, torch
sys.path.insert(0, ".")
from swipe.beam import build_trie, beam_search
from swipe.evaluate import normalize_word, extended_lexicon
from swipe.layout import Layout
from swipe.lexicon import Lexicon
from swipe.model import SwipeEncoder

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="runs/encoder/best.pt")
ap.add_argument("--split", default="test")
ap.add_argument("--extend", action="store_true")
ap.add_argument("--limit", type=int, default=0)
ap.add_argument("--beam", type=int, default=100)
ap.add_argument("--lexicon", default="")
ap.add_argument("--procs", type=int, default=30)
ap.add_argument("--dump", default="")
ap.add_argument("--gamma-prune", type=float, default=None)
ap.add_argument("--beta-prune", type=float, default=None)
ap.add_argument("--gamma-score", type=float, default=None)
ap.add_argument("--lambda-freq", type=float, default=None)
ap.add_argument("--beta-len", type=float, default=None)
A = ap.parse_args()

lay = Layout.from_json("data/hf/swipe-5/layouts/qwerty.json", aspect=422/170.3125)
traces = np.load(f"data/packed/{A.split}/traces.npy")
words = [normalize_word(w) for w in open(f"data/packed/{A.split}/words.txt").read().split("\n")]
keep = [i for i, w in enumerate(words) if len(w) >= 2]
if A.limit:
    keep = keep[:A.limit]

lx = Lexicon(A.lexicon) if A.lexicon else Lexicon()
if A.extend:
    lx = extended_lexicon(lx, {words[i] for i in keep})

ck = torch.load(A.ckpt, map_location="cuda", weights_only=False)
model = SwipeEncoder().cuda().eval()
model.load_state_dict({k.replace("_orig_mod.", ""): v for k, v in ck["model"].items()})

keys = torch.tensor(np.stack([lay.cx, lay.cy], 1), dtype=torch.float32, device="cuda")

t0 = time.time()
logps = np.empty((len(keep), 32, 27), np.float32)
with torch.no_grad():
    for s in range(0, len(keep), 4096):
        sl = keep[s:s + 4096]
        xy = torch.from_numpy(traces[sl].transpose(0, 2, 1).astype(np.float32)).cuda()
        k = keys.unsqueeze(0).expand(len(sl), -1, -1)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            lp, _ = model(xy, k)
        logps[s:s + len(sl)] = lp.float().cpu().numpy()
fwd = time.time() - t0

TRIE = build_trie(lx.words, lx.scores)
KW = {k: v for k, v in [
    ("gamma_prune", A.gamma_prune), ("beta_prune", A.beta_prune),
    ("gamma_score", A.gamma_score), ("lambda_freq", A.lambda_freq),
    ("beta_len", A.beta_len)] if v is not None}

def work(j):
    return beam_search(logps[j], TRIE, beam_width=A.beam, max_results=10, **KW)

if __name__ == "__main__":
    t1 = time.time()
    with Pool(A.procs) as p:
        res = p.map(work, range(len(keep)), chunksize=32)
    bs = time.time() - t1
    n = len(keep)
    tgt = [words[i] for i in keep]
    g = [[w for w, _ in r] for r in res]
    a1 = sum(bool(x) and x[0] == t for x, t in zip(g, tgt)) / n
    a3 = sum(t in x[:3] for x, t in zip(g, tgt)) / n
    a10 = sum(t in x[:10] for x, t in zip(g, tgt)) / n
    base = A.lexicon.split("/")[-1] if A.lexicon else "app35k"
    tag = base + ("+eval" if A.extend else "")
    print(f"NEURAL  {A.split}  lexicon={tag}  beam={A.beam}  n={n:,}  step={ck.get('step')}")
    print(f"  top-1 {a1:7.2%}   top-3 {a3:7.2%}   top-10 {a10:7.2%}")
    print(f"  forward {fwd:.1f}s ({n/fwd:,.0f}/s)   beam {bs:.0f}s on {A.procs} procs")
    if A.dump:
        np.save(A.dump, logps)
        print(f"  emissions -> {A.dump}")
