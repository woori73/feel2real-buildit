import json, os, glob, collections, datetime, io, sys

SRC = r"C:/Users/VIEW LIFE/Documents/Qoder/2026-10-03/1df156eb"
CORPUS = os.path.join(SRC, "corpus")
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def load(p):
    out = []
    with open(p, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out

idx = load(os.path.join(CORPUS, "_index.jsonl"))
skipped = load(os.path.join(CORPUS, "_skipped.jsonl"))

# board id -> name from article jsons
bid = collections.Counter()
names = {}
for d in ("31312978", "_noise"):
    for fp in glob.glob(os.path.join(CORPUS, d, "*.json")):
        a = json.load(open(fp, encoding='utf-8'))
        b = a.get("boardId")
        bid[(b, a.get("boardName"))] += 1
        names[b] = a.get("boardName")

print("== boardId -> boardName (from article files) ==")
for (b, n), c in sorted(bid.items(), key=lambda x: -x[1]):
    print(f"  menuId={b:<6} {n:<20} files={c}")

def summarize(rows, label):
    print(f"\n== {label}: {len(rows)} ==")
    per = collections.Counter(r.get("board") for r in rows)
    for b, c in per.most_common():
        print(f"  {b} ({names.get(b,'?')}): {c}")

summarize(idx, "informational (_index.jsonl)")
summarize(skipped, "skipped (_skipped.jsonl)")

# date range + monthly activity per board
def month(ms):
    return datetime.datetime.utcfromtimestamp(ms/1000 + 9*3600).strftime("%Y-%m")

mon = collections.defaultdict(lambda: collections.Counter())
for r in idx:
    mon[month(r["date"])][r["board"]] += 1
print("\n== monthly informational volume (collected only) ==")
for m in sorted(mon):
    tot = sum(mon[m].values())
    print(f"  {m}  total={tot:<5} " + "  ".join(f"{k}:{v}" for k, v in sorted(mon[m].items())))

# full-scan monthly (incl. skipped) => true activity
mon2 = collections.defaultdict(lambda: collections.Counter())
for r in idx + skipped:
    mon2[month(r["date"])][r["board"]] += 1
print("\n== monthly ALL posts (informational + skipped) ==")
for m in sorted(mon2):
    tot = sum(mon2[m].values())
    print(f"  {m}  total={tot:<5} " + "  ".join(f"{k}:{v}" for k, v in sorted(mon2[m].items())))

# id span per board (article id range = rough chronology)
print("\n== articleId span per board ==")
sp = collections.defaultdict(list)
for r in idx + skipped:
    sp[r["board"]].append(int(r["id"]))
for b, v in sorted(sp.items()):
    print(f"  {b} ({names.get(b,'?')}): n={len(v)} min={min(v)} max={max(v)}")

# operator replies
op = collections.Counter(); opc = 0
for fp in glob.glob(os.path.join(CORPUS, "31312978", "*.json")):
    a = json.load(open(fp, encoding='utf-8'))
    lv = collections.Counter()
    for c in a.get("comments", []):
        lv[c.get("level")] += 1
    has_op = any(k == "카페매니저" for k in lv)
    for k, v in lv.items():
        op[k] += v
    if has_op:
        opc += 1
print("\n== comment levels across collected articles ==")
for k, v in op.most_common(12):
    print(f"  {k}: {v}")
print(f"  articles with >=1 cafe-manager comment: {opc}")

# read/comment stats
rc = [int(json.load(open(fp, encoding="utf-8")).get("readCount") or 0)
      for fp in glob.glob(os.path.join(CORPUS, "31312978", "*.json"))]
rc.sort()
n = len(rc)
print(f"\n== readCount (collected) == n={n} mean={sum(rc)/n:.0f} p50={rc[n//2]} p90={rc[int(n*.9)]} p99={rc[int(n*.99)]} max={rc[-1]}")
