import math, re, os

MAX_WORDS = 35000
ok = re.compile(r"^[a-z]+$")

freq = {}
with open("en_50k.txt") as f:
    for line in f:
        p = line.split()
        if len(p) != 2:
            continue
        w, c = p[0], int(p[1])
        if not ok.match(w):
            continue
        if len(w) < 1 or len(w) > 18:
            continue
        if len(w) == 1 and w not in ("a", "i"):
            continue
        freq[w] = freq.get(w, 0) + c

# Google 10k: give every word at least a floor frequency based on its rank so
# that common words missing from the subtitle corpus still rank sensibly.
with open("g10k.txt") as f:
    g = [w.strip() for w in f if ok.match(w.strip())]
for rank, w in enumerate(g):
    if len(w) == 1 and w not in ("a", "i"):
        continue
    floor = int(3_000_000 / (rank + 12))
    freq[w] = max(freq.get(w, 0), floor)

# Contractions: the subtitle corpus tokenises them away, but swipe users expect
# "dont" -> "don't". The apostrophe is skipped when the key path is built.
CONTRACTIONS = """i'm i've i'll i'd you're you've you'll you'd we're we've we'll we'd
they're they've they'll they'd he's he'll he'd she's she'll she'd it's it'll that's
that'll there's there'll who's who'll what's what're where's when's how's let's
don't doesn't didn't isn't aren't wasn't weren't haven't hasn't hadn't won't wouldn't
can't couldn't shouldn't mustn't needn't ain't y'all o'clock she'd've'"""
for w in CONTRACTIONS.split():
    w = w.strip("'")
    if not w or "'" not in w:
        continue
    bare = w.replace("'", "")
    # Base the contraction's weight on its stem so ranking stays sane.
    stem = freq.get(w.split("'")[0], 200000)
    freq[w] = max(freq.get(w, 0), int(stem * 0.55))

items = sorted(freq.items(), key=lambda kv: (-kv[1], kv[0]))[:MAX_WORDS]

os.makedirs("out", exist_ok=True)
with open("out/swipe_words.txt", "w") as f:
    f.write("# word<TAB>logscore  (score = round(log2(count)*10), 1..255)\n")
    for w, c in items:
        s = max(1, min(255, round(math.log2(max(c, 2)) * 10)))
        f.write(f"{w}\t{s}\n")

print("words:", len(items))
print("bytes:", os.path.getsize("out/swipe_words.txt"))
lens = [len(w) for w, _ in items]
print("avg len:", sum(lens) / len(lens), "max:", max(lens))
