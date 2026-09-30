import json, os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B6 = os.path.join(BASE, "cafe-qa-phase1-b6")
OUT = os.path.join(B6, "load_batches")
os.makedirs(OUT, exist_ok=True)

db = json.load(open(os.path.join(B6, "cafe_qa_db_ready.json"), encoding="utf-8"))
units = json.load(open(os.path.join(B6, "qa_units.json"), encoding="utf-8"))
units = units["units"] if isinstance(units, dict) and "units" in units else units
by_key = {(x["articleId"], x["unitType"]): x for x in db["items"]}

AUTH = {
    "QUESTION": "실제로 많이 묻는 질문", "ANSWER": "회원 답변",
    "ACTUAL_CASE": "실제 신청·실효·전환 사례", "MEMBER_EXPERIENCE": "회원 경험담",
    "COUNSELOR_CLAIM": "상담사 주장(확인 필요)", "STAFF_ANSWER": "운영자 답변",
    "OPERATOR_GUIDE": "운영자 안내", "COMMUNITY_OPINION": "커뮤니티 의견",
    "INFERENCE": "추론(검토 필요)",
}
# 신호 높은 유형 우선, 커뮤니티 의견은 마지막
ORDER = ["ACTUAL_CASE", "OPERATOR_GUIDE", "STAFF_ANSWER", "ANSWER",
         "MEMBER_EXPERIENCE", "QUESTION", "COUNSELOR_CLAIM", "COMMUNITY_OPINION", "INFERENCE"]
RANK = {t: i for i, t in enumerate(ORDER)}

LOADED = {"u-20-002", "u-33-001", "u-37-001", "u-63-001",
          "u-208-001", "u-2559-001", "u-1024-001", "u-5372-002"}

def aid_of(uid):
    return int(uid.split("-")[1])

reqs = []
for x in units:
    uid = x["unitId"]
    if uid in LOADED:
        continue
    aid = aid_of(uid)
    utype = x["type"]
    src = by_key.get((aid, utype), {})
    stype = src.get("sourceType", "CAFE_ARTICLE")
    label = AUTH.get(utype, utype)
    ev = (x.get("evidenceText") or "").strip()
    if not ev:
        continue
    title = src.get("title") or f"[카페Q&A b6#{aid}·{label}] {ev[:40]}"
    dkey = src.get("dedupKey", f"CAFE_QA:b6:{aid}:{utype}:x")
    sd = (src.get("sourceDate") or x.get("createdAt") or "")[:10]
    reqs.append({
        "unitId": uid,
        "rank": RANK.get(utype, 99),
        "req": {
            "sourceType": stype,
            "category": "부실우려",
            "title": title[:200],
            "summary": f"출처 article {aid} · {label} · 공식확인 전(NOT_CHECKED)",
            "content": ev[:600],
            "authority": "부실우려 카페Q&A REVIEW 적재(b6)",
            "tags": f"CAFE_QA,b6,menuId6,unit={utype},officialCheck=NOT_CHECKED,ck={dkey}",
            "sourceUrl": src.get("sourceUrl") or x.get("sourceUrl"),
            "sourceDate": sd or None,
            "confidence": src.get("confidence") or x.get("confidence"),
        },
    })

reqs.sort(key=lambda r: (r["rank"], aid_of(r["unitId"])))
payloads = [r["req"] for r in reqs]

B = 100
batches = [payloads[i:i+B] for i in range(0, len(payloads), B)]
order_uids = [r["unitId"] for r in reqs]
for i, b in enumerate(batches):
    fn = os.path.join(OUT, f"batch_{i:02d}.json")
    json.dump(b, open(fn, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

manifest = {
    "totalToLoad": len(payloads),
    "batchSize": B,
    "batchCount": len(batches),
    "loadedPilot": sorted(LOADED),
    "orderNote": "ACTUAL_CASE/OPERATOR_GUIDE/STAFF_ANSWER/ANSWER/MEMBER_EXPERIENCE/QUESTION/COUNSELOR_CLAIM 먼저, COMMUNITY_OPINION 마지막",
    "batches": [{"file": f"batch_{i:02d}.json", "count": len(b),
                 "unitIds_first": b[0]["tags"] if b else None} for i, b in enumerate(batches)],
}
json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"),
          ensure_ascii=False, indent=1)


# 로드 순서(유형별) 확인
from collections import Counter
print("total_to_load", len(payloads), "batches", len(batches))
print("first5 titles:")
for p in payloads[:5]:
    print("  ", p["title"][:60])
print("last5 titles:")
for p in payloads[-5:]:
    print("  ", p["title"][:60])
print("sizes:", [len(json.dumps(b, ensure_ascii=False)) for b in batches])
