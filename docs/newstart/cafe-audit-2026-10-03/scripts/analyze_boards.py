import json, io, sys, collections, datetime, re, pathlib

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.loads(pathlib.Path('audit_raw/menu_data.json').read_text(encoding='utf-8'))['data']

NAME = {'23':'지피지기 agent','14':'공지사항','16':'초보가이드','1':'질문게시판','13':'자유게시판',
        '15':'재기를 꿈꾼다','11':'추심 민원 샘플','6':'부실우려','7':'부동산 차량 등 담보',
        '2':'부실차주 자격 연체 추심','3':'부실차주 신청','4':'부실차주 임시안부터 확정안',
        '5':'재취업 재창업 감면수업 + 정부 지원','9':'하랑랑 스페셜','22':'감면율과 확정금액 예측 게시판'}

PROBE = {
 '부실우려': r'부실우려', '부실차주': r'부실차주', '신청': r'신청', '임시안': r'임시안',
 '확정안': r'확정안', '감면율': r'감면율|감면', '담보': r'담보|경매|전세|주택|차량|공매',
 '추심': r'추심|독촉', '지급명령': r'지급명령', '연체': r'연체', '신복위': r'신복위|회복위|워크아웃',
 '교육': r'교육|희망리턴|재취업|재창업', '예측': r'예측', '전자책': r'전자책|책',
}

now = datetime.datetime(2026, 10, 3, 23, 59).timestamp() * 1000
print(f"{'menuId':>6} {'게시판':<22} {'글':>5} {'매니저%':>7} {'문의%':>6} {'90일무활동':>9}  주요 신호(글 수)")
rows = []
for mid, items in sorted(d.items(), key=lambda kv: int(kv[0])):
    n = len(items)
    mgr = round(100 * sum(1 for x in items if x['mgr']) / n)
    q = round(100 * sum(1 for x in items if '?' in (x['s'] or '')) / n)
    latest = max((x['t'] or 0) for x in items)
    silent = round((now - latest) / 864e5)
    blob = ' '.join((x['s'] or '') for x in items)
    hits = {k: len(re.findall(p, blob)) for k, p in PROBE.items()}
    top = sorted(((k, v) for k, v in hits.items() if v), key=lambda kv: -kv[1])[:5]
    rows.append((mid, n, mgr, q, silent, top))
    print(f"{mid:>6} {NAME[mid]:<22} {n:>5} {mgr:>6}% {q:>5}% {silent:>8}d  " +
          ' '.join(f"{k}:{v}" for k, v in top))

print("\n== 합계 ==", sum(r[1] for r in rows))

# overlap matrix: jaccard of keyword signature between boards (for MERGE candidates)
sig = {mid: {k for k, v in hits.items() if False} for mid, items in []}
sigs = {}
for mid, items in d.items():
    blob = ' '.join((x['s'] or '') for x in items)
    sigs[mid] = {k for k, p in PROBE.items() if re.search(p, blob)}
ids = sorted(sigs, key=int)
print("\n== 게시판 간 제목 키워드 서명 겹침 (Jaccard) ==")
pairs = []
for i in range(len(ids)):
    for j in range(i + 1, len(ids)):
        a, b = sigs[ids[i]], sigs[ids[j]]
        if not a or not b:
            continue
        jac = len(a & b) / len(a | b)
        if jac >= 0.5:
            pairs.append((jac, ids[i], ids[j], sorted(a & b)))
for jac, a, b, common in sorted(pairs, reverse=True)[:12]:
    print(f"  {NAME[a]} <-> {NAME[b]}  J={jac:.2f}  common={','.join(common)}")
