#!/usr/bin/env python3
"""manifest → POST /api/admin/newstart/wiki 용 REVIEW payload (dedupe PASS 17건).

제외(자동): payloadReady=false(6040·6044·4078) / HOLD(4451, 798001 RAW 미러) / DEDUPED(6050 ↔ 운영 118·119).
화자는 닉네임 대신 역할로 표기. 5568 Q14·Q15 답변은 SAFETY REVIEW 대기 문구로 치환(원문은 raw 보존).
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
M = json.load(open(os.path.join(HERE, "INGEST_READY_MANIFEST_AB_2026-09-30.json"), encoding="utf-8"))["items"]

HOLD = {"AB-4451": "HOLD(재산심사 회피성 조언 포함)", "AB-798001": "HOLD(RAW 미러, K1~K7 우선)"}
DEDUPED = {"AB-6050": "운영 knowledge 118·119 와 내용 중복(2024 기준선 vs 2026-08 변화 관찰)"}

SRC_TYPE = {"AB-3401": "OPERATOR_KNOWLEDGE", "AB-3438": "OPERATOR_KNOWLEDGE", "AB-5568": "OPERATOR_KNOWLEDGE",
            "AB-6037": "CAFE_ARTICLE", "AB-6038": "CAFE_ARTICLE", "AB-6043": "CAFE_ARTICLE",
            "AB-6046": "CAFE_ARTICLE", "AB-6047": "CAFE_ARTICLE", "AB-6055": "CASE", "AB-799029": "CASE",
            "AB-798001-K1": "CAFE_ARTICLE", "AB-798001-K2": "CAFE_ARTICLE", "AB-798001-K3": "CAFE_ARTICLE",
            "AB-798001-K4": "CASE", "AB-798001-K5": "CASE", "AB-798001-K6": "CASE", "AB-798001-K7": "CASE"}
CATEGORY = {"AB-6037": "부동산 차량 등 담보", "AB-6038": "부실차주 자격·연체·추심", "AB-6043": "부실차주 신청",
            "AB-6046": "부실우려", "AB-6047": "부실차주 자격·연체·추심", "AB-6055": "부실차주 사례",
            "AB-799029": "기타", "AB-3401": "부동산 차량 등 담보", "AB-3438": "감면율", "AB-5568": "부실차주 신청"}
KIND = {"OPERATOR_KNOWLEDGE": "운영자 안내", "CAFE_ARTICLE": "질문·운영자 답변", "CASE": "실제 사례"}
CONF = {"AB-798001-K1": "MEDIUM", "AB-798001-K2": "MEDIUM", "AB-798001-K3": "UNKNOWN", "AB-798001-K4": "UNKNOWN",
        "AB-798001-K5": "UNKNOWN", "AB-798001-K6": "UNKNOWN", "AB-798001-K7": "UNKNOWN"}
AUTH_K = {"AB-798001-K1": "신용회복위원회 (카페 게시 자료, 공식 확인 대기)", "AB-798001-K2": "신용회복위원회 (카페 게시 자료, 공식 확인 대기)",
          "AB-798001-K3": "게시자 해석 (2차 정보)", "AB-798001-K4": "미확정 (상담 제보)", "AB-798001-K5": "미확정 (상담 제보)",
          "AB-798001-K6": "미확정 (커뮤니티 추정)", "AB-798001-K7": "미확정 (게시자 해석)"}

def role(s):
    s = s or ""
    if "운영자" in s or "매니저" in s: return "운영자"
    if "댓글 회원" in s: return "댓글 회원"
    if "작성자" in s: return "작성자"
    return "회원"

LABEL_KO = {"CLAIM": "CLAIM(주장·운영 경험, 공식 아님)", "EXPERIENCE": "EXPERIENCE(회원 경험)",
            "CONFIRMED": "CONFIRMED(사용자 캡처 대조, 공식 링크 재대조 권장)", "INFERENCE": "INFERENCE(추정, 규칙 아님)"}

def raw_body_5568():
    t = open(os.path.join(HERE, "raw", "article_5568_raw.txt"), encoding="utf-8").read()
    body = t.split("--- BEGIN BODY ---", 1)[1].split("--- END BODY ---")[0] if "--- BEGIN BODY ---" in t else t.split("\n\n", 1)[1]
    parts = re.split(r"(?m)^(?=Q\d+\.)", body)
    out = []
    for p in parts:
        m = re.match(r"Q(\d+)\.", p)
        if m and int(m.group(1)) in (14, 15):
            q = p.split("\n", 1)[0]
            out.append(f"{q}\n답변: [SAFETY REVIEW 대기 — 재산 정리 관련 답변, 원문은 raw 보존·승인 전 재검수]\n\n")
        else:
            out.append(p)
    return "".join(out).strip()

def content_of(i):
    mid = i["manifestId"]; s = i["source"]
    lines = [f"출처: {s['community']} / {s['board']} / 글번호 {s['articleId']} / {s['writtenAt']}",
             f"근거 수준: {i['evidenceType']} — 카페 발언은 공식 규정이 아니며 공식 확인 전(NOT_CHECKED)"]
    for seg in i["segments"]:
        lab = f" [{LABEL_KO.get(seg.get('label'), seg.get('label'))}]" if seg.get("label") else ""
        lines.append(f"\n[{seg['type']} · {role(seg.get('speaker'))}]{lab}\n{seg['text']}" + (f"\n(비고: {seg['note']})" if seg.get("note") else ""))
    if mid == "AB-5568":
        lines.append("\n[원문 Q&A 본문 — Q14·Q15 답변은 안전 검수 대기로 치환]\n" + raw_body_5568())
    flags = i["flags"]
    if mid == "AB-5568":  # 치환한 조언의 내용을 주의 문구에 되풀이하지 않는다
        flags = ["재산 정리 관련 Q14·Q15 답변은 안전 검수 대기로 치환됨(승인 전 재검수 필수)", "운영자 안내이며 공식 규정 아님"]
    if flags:
        lines.append("\n[주의]\n" + "\n".join("- " + f for f in flags))
    return "\n".join(lines)

payloads, skipped = [], []
for i in M:
    mid = i["manifestId"]
    if mid in HOLD: skipped.append((mid, "HOLD", HOLD[mid])); continue
    if mid in DEDUPED: skipped.append((mid, "DEDUPED", DEDUPED[mid])); continue
    if not i["payloadReady"]: skipped.append((mid, "EXCLUDED", i["approvalRecommendation"])); continue
    st = SRC_TYPE[mid]; s = i["source"]; k = mid.startswith("AB-798001-K")
    title = i["title"] if k else f"[카페 #{s['articleId']}·{KIND[st]}] {i['title']}"
    label = i["evidenceType"] if k else ("EXPERIENCE" if st == "CASE" else "CLAIM")
    tags = ",".join(["CASE_COLLECTOR_AB", f"manifest={mid}", f"dedup={i['dedup_key']}", f"fp={i['fingerprint']}",
                     f"evidence={label}", "officialCheck=NOT_CHECKED", f"club={s['clubId']}"])
    d = s["writtenAt"][:10]
    payloads.append({
        "manifestId": mid, "sourceArticleId": s["articleId"],
        "request": {
            "sourceType": st, "category": "재산심사" if k else CATEGORY[mid], "title": title[:200],
            "summary": (i["segments"][0]["text"][:190] + " · 공식확인 전(NOT_CHECKED)") if not k else (i["title"][:160] + " · 공식확인 전"),
            "content": content_of(i),
            "authority": AUTH_K[mid] if k else "카페 회원·운영자 발언 (비공식)",
            "tags": tags, "sourceUrl": s["url"], "sourceDate": d,
            "confidence": CONF.get(mid, "MEDIUM"),
        }})

# 사전 검증 (HARD GATE 일부)
titles = [p["request"]["title"] for p in payloads]
assert len(set(titles)) == len(titles)
for p in payloads:
    r = p["request"]
    assert len(r["title"]) <= 200 and len(r["category"]) <= 50 and len(r["confidence"]) <= 20 and len(r["authority"]) <= 50, p["manifestId"]
    assert "지피지기자산" not in r["content"], p["manifestId"]
    assert "[SAFETY_FLAG]" not in r["content"], p["manifestId"]
    assert r["sourceType"] in ("OPERATOR_KNOWLEDGE", "CAFE_ARTICLE", "CASE")
assert len(payloads) + len(skipped) == len(M) == 23

out = {"generatedAt": "2026-09-30", "count": len(payloads), "skipped": skipped, "payloads": payloads}
json.dump(out, open(os.path.join(HERE, "REVIEW_PAYLOADS_AB_2026-09-30.json"), "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=1)
print(len(payloads), "payloads;", len(skipped), "skipped")
for s in skipped: print(" skip", s)
print("content bytes", sum(len(p["request"]["content"].encode()) for p in payloads))
