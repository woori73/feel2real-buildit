import json, os, glob, collections, datetime, io, sys, re

SRC = r"C:/Users/VIEW LIFE/Documents/Qoder/2026-10-03/1df156eb"
CORPUS = os.path.join(SRC, "corpus")
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def load_jsonl(p):
    with open(p, encoding='utf-8') as f:
        return [json.loads(l) for l in f if l.strip()]

idx = load_jsonl(os.path.join(CORPUS, "_index.jsonl"))
skipped = load_jsonl(os.path.join(CORPUS, "_skipped.jsonl"))

print("== skipReason distribution (1786) ==")
for k, v in collections.Counter(r["skipReason"] for r in skipped).most_common():
    print(f"  {k:<16} {v}")

print("\n== skipReason x board ==")
xb = collections.Counter((r["board"], r["skipReason"]) for r in skipped)
for (b, s), v in sorted(xb.items(), key=lambda x: -x[1]):
    print(f"  {b:<22} {s:<16} {v}")

arts = {}
for d in ("31312978", "_noise"):
    for fp in glob.glob(os.path.join(CORPUS, d, "*.json")):
        a = json.load(open(fp, encoding='utf-8'))
        arts[int(a["id"])] = a
print(f"\n== article json files loaded: {len(arts)} ==")

def mth(a):
    d = a.get("writeDate")
    if isinstance(d, (int, float)):
        return datetime.datetime.utcfromtimestamp(d/1000 + 9*3600).strftime("%Y-%m")
    s = str(d)[:7]
    return s

mon_all = collections.defaultdict(collections.Counter)
mon_info = collections.defaultdict(collections.Counter)
infoset = {int(r["id"]) for r in idx}
for i, a in arts.items():
    m = mth(a)
    b = a["boardName"]
    mon_all[m][b] += 1
    if i in infoset:
        mon_info[m][b] += 1

print("\n== monthly ALL posts (dates from article files) ==")
for m in sorted(mon_all):
    t = sum(mon_all[m].values())
    print(f"  {m} total={t:<5} " + " | ".join(f"{k}:{v}" for k, v in sorted(mon_all[m].items())))

dates = [a.get("writeDate") for a in arts.values() if isinstance(a.get("writeDate"), (int, float))]
if dates:
    f = lambda ms: datetime.datetime.utcfromtimestamp(ms/1000+9*3600).strftime("%Y-%m-%d")
    print(f"\n== period == {f(min(dates))} ~ {f(max(dates))}  (n={len(dates)})")
else:
    print("\nwriteDate sample:", repr(next(iter(arts.values())).get("writeDate")))

# topic keyword distribution over informational bodies (short labels only)
KW = {
    "추심/독촉": r"추심|독촉|집합|방문추심",
    "연체/연체금": r"연체",
    "기한이익상실": r"기한이익|기한의 이익",
    "지급명령": r"지급명령",
    "새출발기금 신청": r"새출발기금",
    "Credit4U/신용": r"credit4u|크레디트4유|신용점수|신용등급",
    "담보/주택/차량": r"담보|경매|주택|아파트|차량|경매진행",
    "채권자/대부": r"채권자|대부|캐피탈|저축은행",
    "개인회생/파산": r"개인회생|파산|면책",
    "배분/변제율": r"배분|변제율|원금감면|감면",
    "불이익/불승인": r"불이익|불승인|각하|부결",
    "1:1/상담/운영자": r"상담|카페매니저|운영자",
}
print("\n== keyword coverage in informational bodies (article counts) ==")
byboard = collections.defaultdict(lambda: collections.Counter())
tot = collections.Counter()
for i in infoset:
    a = arts.get(i)
    if not a:
        continue
    txt = (a.get("subject") or "") + " " + (a.get("body") or "")
    for k, pat in KW.items():
        if re.search(pat, txt, re.I):
            tot[k] += 1
            byboard[a["boardName"]][k] += 1
for k in sorted(tot, key=lambda x: -tot[x]):
    per = " ".join(f"{b.split()[0]}:{byboard[b][k]}" for b in byboard if byboard[b][k])
    print(f"  {k:<18} {tot[k]:<5} ({tot[k]/len(infoset)*100:.0f}%)  {per}")

# top boards by operator answer
op_art = collections.Counter()
for i in infoset:
    a = arts.get(i)
    if a and any(c.get("level") == "카페매니저" for c in a.get("comments", [])):
        op_art[a["boardName"]] += 1
print("\n== informational articles with cafe-manager reply, by board ==")
for k, v in op_art.most_common():
    print(f"  {k}: {v}")

# readCount by board
rb = collections.defaultdict(list)
for i in infoset:
    a = arts.get(i)
    if a:
        rb[a["boardName"]].append(int(a.get("readCount") or 0))
print("\n== readCount by board (informational) ==")
for b, v in rb.items():
    v.sort()
    print(f"  {b}: n={len(v)} mean={sum(v)/len(v):.0f} p50={v[len(v)//2]} max={v[-1]}")
