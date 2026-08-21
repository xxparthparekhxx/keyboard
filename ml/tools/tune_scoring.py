"""Two-stage tune of the pruning and scoring constants, on the dev split.

Stage 1 (pruning) has to re-run beam search per trial, so it is swept coarsely
against beam recall. Stage 2 (scoring) is pure arithmetic over cached candidate
lists, so it gets thousands of trials for free.

The paper's published constants were fit against a 162k AOSP lexicon; this app
ships 35k with a different frequency scale, so they do not transfer and have to
be refit here.
"""
import argparse, pickle, sys, time
from multiprocessing import Pool
import numpy as np, optuna, torch
sys.path.insert(0, ".")
optuna.logging.set_verbosity(optuna.logging.WARNING)
from swipe.beam import build_trie, beam_search, rank
from swipe.evaluate import normalize_word, extended_lexicon
from swipe.layout import Layout
from swipe.lexicon import Lexicon
from swipe.model import SwipeEncoder

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="runs/encoder/best.pt")
ap.add_argument("--split", default="dev")
ap.add_argument("--n", type=int, default=12000)
ap.add_argument("--beam", type=int, default=100)
ap.add_argument("--extend", action="store_true")
ap.add_argument("--prune-trials", type=int, default=40)
ap.add_argument("--score-trials", type=int, default=3000)
ap.add_argument("--procs", type=int, default=30)
A = ap.parse_args()

lay = Layout.from_json("data/hf/swipe-5/layouts/qwerty.json", aspect=422/170.3125)
traces = np.load(f"data/packed/{A.split}/traces.npy")
words = [normalize_word(w) for w in open(f"data/packed/{A.split}/words.txt").read().split("\n")]
keep = [i for i, w in enumerate(words) if len(w) >= 2][:A.n]
tgt = [words[i] for i in keep]

lx = Lexicon()
if A.extend:
    lx = extended_lexicon(lx, set(tgt))
TRIE = build_trie(lx.words, lx.scores)

ck = torch.load(A.ckpt, map_location="cuda", weights_only=False)
m = SwipeEncoder().cuda().eval()
m.load_state_dict({k.replace("_orig_mod.", ""): v for k, v in ck["model"].items()})
keys = torch.tensor(np.stack([lay.cx, lay.cy], 1), dtype=torch.float32, device="cuda")
LOGP = np.empty((len(keep), 32, 27), np.float32)
with torch.no_grad():
    for s in range(0, len(keep), 4096):
        sl = keep[s:s+4096]
        xy = torch.from_numpy(traces[sl].transpose(0, 2, 1).astype(np.float32)).cuda()
        with torch.autocast("cuda", dtype=torch.bfloat16):
            lp, _ = m(xy, keys.unsqueeze(0).expand(len(sl), -1, -1))
        LOGP[s:s+len(sl)] = lp.float().cpu().numpy()
print(f"emissions ready: {LOGP.shape}, step={ck.get('step')}")

_GP = [0.186, 1.139]

def _cands(j):
    return beam_search(LOGP[j], TRIE, beam_width=A.beam, return_candidates=True,
                       gamma_prune=_GP[0], beta_prune=_GP[1])

def gather(gp, bp, procs):
    global _GP
    _GP = [gp, bp]
    with Pool(procs, initializer=_init, initargs=(gp, bp)) as p:
        return p.map(_cands, range(len(keep)), chunksize=32)

def _init(gp, bp):
    global _GP
    _GP = [gp, bp]

def recall(cands, k):
    hit = 0
    for c, t in zip(cands, tgt):
        if any(w == t for w, _, _, _ in c[:k] if True) or any(w == t for w, _, _, _ in c):
            hit += 1
    return hit / len(tgt)

if __name__ == "__main__":
    # ---- stage 1: pruning, scored on beam recall ----
    t0 = time.time()
    best = (None, -1)
    def prune_obj(trial):
        gp = trial.suggest_float("gamma_prune", 0.0, 1.0)
        bp = trial.suggest_float("beta_prune", 0.0, 4.0)
        c = gather(gp, bp, A.procs)
        r = recall(c, A.beam)
        return r
    st = optuna.create_study(direction="maximize",
                             sampler=optuna.samplers.TPESampler(seed=0))
    st.optimize(prune_obj, n_trials=A.prune_trials)
    gp, bp = st.best_params["gamma_prune"], st.best_params["beta_prune"]
    print(f"stage1 pruning: gamma_prune={gp:.4f} beta_prune={bp:.4f} "
          f"recall={st.best_value:.4f}  ({time.time()-t0:.0f}s)")

    # ---- stage 2: scoring, over cached candidates ----
    cands = gather(gp, bp, A.procs)
    with open("runs/dev_candidates.pkl", "wb") as f:
        pickle.dump({"cands": cands, "tgt": tgt, "gp": gp, "bp": bp}, f)

    def score_obj(trial):
        g = trial.suggest_float("gamma_score", 0.0, 1.0)
        lf = trial.suggest_float("lambda_freq", 0.0, 0.3)
        bl = trial.suggest_float("beta_len", 0.0, 6.0)
        t1 = t3 = 0
        for c, t in zip(cands, tgt):
            r = rank(c, 3, g, lf, bl)
            if r and r[0][0] == t: t1 += 1
            if any(w == t for w, _ in r): t3 += 1
        n = len(tgt)
        return 0.75 * t1 / n + 0.25 * t3 / n

    st2 = optuna.create_study(direction="maximize",
                              sampler=optuna.samplers.TPESampler(seed=0))
    st2.optimize(score_obj, n_trials=A.score_trials, n_jobs=1)
    p = st2.best_params
    print(f"stage2 scoring: gamma_score={p['gamma_score']:.4f} "
          f"lambda_freq={p['lambda_freq']:.4f} beta_len={p['beta_len']:.4f}")

    t1 = sum(bool(r) and r[0][0] == t for r, t in
             ((rank(c, 1, p['gamma_score'], p['lambda_freq'], p['beta_len']), t)
              for c, t in zip(cands, tgt)))
    print(f"dev top-1 at optimum: {t1/len(tgt):.4f}")
    print(f"\nKotlin constants:\n"
          f"  GAMMA_PRUNE = {gp:.4f}f\n  BETA_PRUNE = {bp:.4f}f\n"
          f"  GAMMA_SCORE = {p['gamma_score']:.4f}f\n"
          f"  LAMBDA_FREQ = {p['lambda_freq']:.4f}f\n  BETA_LEN = {p['beta_len']:.4f}f")
