import json, os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B6 = os.path.join(BASE, "cafe-qa-phase1-b6")

db = json.load(open(os.path.join(B6, "cafe_qa_db_ready.json"), encoding="utf-8"))
units = json.load(open(os.path.join(B6, "qa_units.json"), encoding="utf-8"))
units = units["units"] if isinstance(units, dict) and "units" in units else units
by_unit = {x["unitId"]: x for x in units}
by_key = {(x["articleId"], x["unitType"]): x for x in db["items"]}

# unitId -> articleId via u-{aid}-{seq}
def aid_of(uid):
    return int(uid.split("-")[1])

# human Korean authority label by unitType
AUTH = {
    "QUESTION": "실제로 많이 묻는 질문",
    "ANSWER": "회원 답변",
    "ACTUAL_CASE": "실제 신청·실효·전환 사례",
    "MEMBER_EXPERIENCE": "회원 경험담",
    "COUNSELOR_CLAIM": "상담사 주장(확인 필요)",
    "STAFF_ANSWER": "운영자 답변",
    "OPERATOR_GUIDE": "운영자 안내",
    "COMMUNITY_OPINION": "커뮤니티 의견",
    "INFERENCE": "추론(검토 필요)",
}

PILOT = ["u-20-002", "u-33-001", "u-37-001", "u-63-001",
         "u-208-001", "u-2559-001", "u-1024-001", "u-5372-002"]

payloads = []
for uid in PILOT:
    x = by_unit[uid]
    aid = aid_of(uid)
    utype = x["type"]
    item = by_key.get((aid, utype))
    src = item or {}
    stype = src.get("sourceType", "CAFE_ARTICLE")
    label = AUTH.get(utype, utype)
    ev = (x.get("evidenceText") or "").strip()
    title = src.get("title") or f"[카페Q&A b6#{aid}·{label}] {ev[:40]}"
    dkey = src.get("dedupKey", f"CAFE_QA:b6:{aid}:{utype}:unknown")
    sd = (src.get("sourceDate") or x.get("createdAt") or "")
    sd = sd[:10] if sd else None
    payloads.append({
        "unitId": uid,
        "req": {
            "sourceType": stype,
            "category": "부실우려",
            "title": title[:200],
            "summary": f"출처 article {aid} · {label} · 공식확인 전(NOT_CHECKED)",
            "content": ev[:1800],
            "authority": "부실우려 카페Q&A REVIEW 적재(b6)",
            "tags": f"CAFE_QA,b6,menuId6,unit={utype},officialCheck=NOT_CHECKED,ck={dkey}",
            "sourceUrl": src.get("sourceUrl") or x.get("sourceUrl"),
            "sourceDate": sd,
            "confidence": src.get("confidence") or x.get("confidence"),
            "relatedRuleIds": None,
            "relatedCaseIds": None,
        },
    })

out = {"batchId": db["batchId"], "count": len(payloads), "payloads": payloads}
dest = os.path.join(B6, "pilot_payload.json")
json.dump(out, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
for p in payloads:
    r = p["req"]
    print(p["unitId"], r["sourceType"], "|", r["title"][:48], "| content", len(r["content"]), "| date", r["sourceDate"])
print("WROTE", dest)
