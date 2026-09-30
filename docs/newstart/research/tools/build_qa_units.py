#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PHASE1 STEP2 (부실우려, board/menuId=6) — RAW 248 -> semantic qa_units.json  (v2)

Deterministic, evidence-preserving first-pass structuring. NO browser, NO re-collection.
Input : ../cafe-qa-phase1-b6/articles/article_*.json  (248 RAW from HOME handoff)
Output: ../cafe-qa-phase1-b6/qa_units.json

Meaning units: QUESTION ANSWER ACTUAL_CASE MEMBER_EXPERIENCE COUNSELOR_CLAIM
               STAFF_ANSWER OPERATOR_GUIDE COMMUNITY_OPINION (INFERENCE reserved for humans)

Honoured rules:
- Provenance preserved per unit; cafe answers are NEVER promoted to official ->
  officialCheck=NOT_CHECKED (OFFICIAL_CONFIRMED only after a real official-document match).
- Image-only 540 / 3768 -> IMAGE_ONLY / NEEDS_VISUAL_REVIEW, not guessed, not blockers.
- COUNSELOR_CLAIM fires ONLY on institution-attributed reported speech (상담사/콜센터/신복위/
  새출발기금/법원/센터/담당 … + hearsay verb), not on casual ~하던데/해요.
- One primary unit per body; explicit <질문>/<답변> blocks are split into QUESTION + STAFF_ANSWER.
- Trivial greeting/emoji units are dropped and counted as EXCLUDED (noise).
- INFERENCE is not fabricated by this script.
"""
import json, glob, os, re, hashlib

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.normpath(os.path.join(HERE, "..", "cafe-qa-phase1-b6"))
ARTICLES = os.path.join(DATA, "articles")
OUT = os.path.join(DATA, "qa_units.json")

ZW = "​"
IMAGE_ONLY_IDS = {540, 3768}

def clean(t):
    if not t:
        return ""
    t = t.replace(ZW, "")
    t = re.sub(r"[ \t]+\n", "\n", t)
    t = re.sub(r"\n{2,}", "\n", t)
    return t.strip()

# --- signal dictionaries ---------------------------------------------------
Q_MARK = re.compile(r"(\?|？|인가요|되나요|됩니까|될까요|나요|인가|인줄|아나요|해도되|해도 되는|"
                    r"괜찮나요|괜찮|어떻|어떡|어떤|가능할|되는지|되는지|인지|일까요|까요|문의|질문|알고|알 ?수)")
CASE_MARK = re.compile(r"(신청|접수|약정|납부|납입|실효|전환|부실차주|부실우려|변제|동의|부동의|"
                       r"심사|기각|인용|압류|경매|연체|채무조정|개인워크|개인회생|워크아웃|"
                       r"첫 ?납|합의|성공|진행중|완료|배정|수정조정|대위변제|매입|환매)")
SELF_MARK = re.compile(r"(저는|저도|제 |제가|제때|내 |내가|나는|본인은|직접|우리)")
ADVICE_MARK = re.compile(r"(하세요|하십시오|하셔도|하시면|해보|建议您|~됩니다|됩니다|되어야|권장|"
                         r"-change|변경하시는|비워|미루|기다리|신청하시는|咨询)")
INSTITUTION = re.compile(r"(상담사|콜센|콜센터|상담원|센터|담당|직원|지점|사원|신복위|신용회복|"
                         r"새출발|새 출발|법원|금융위|진흥공단|신용정보|홈텍스|예금보험|예보|"
                         r"워크아웃팀|회생법원)")
HEARSAY = re.compile(r"(라네요|라고 하|하더라|하더|말하|말로는|라고 들|라고 해서|라고 했|"
                     r"해준다|해주더라|안내했|답변했|~라 함|라고 함|라고 답변)")
TRIVIAL = re.compile(r"^[·。，.\s]+$")
THANKS = re.compile(r"^(네|넵|ㅇ|ㅋ| ㅎㅎ|감사?합니다?|감사합니다!!*|고마워요?|수고|확인|좋아|!+|~+|ㅇㅇ|ㄴㄴ|ㄱㄱ)"
                    r"[.!~\sㅋㅎㅠㅜㅇ]*$")

OPERATOR_GRADE = "카페매니저"
QA_MARKER = re.compile(r"[\[<](질문|답변|응답)[\]>]", re.IGNORECASE)

def is_reported_claim(text):
    """Institution token AND hearsay verb both present (attributed claim, not casual speech)."""
    return bool(INSTITUTION.search(text) and HEARSAY.search(text))

def prov_and_official(utype):
    mapping = {
        "QUESTION": "CAFE_QA", "ANSWER": "CAFE_QA", "ACTUAL_CASE": "CAFE_CASE",
        "MEMBER_EXPERIENCE": "MEMBER_EXPERIENCE", "COUNSELOR_CLAIM": "COUNSELOR_CLAIM",
        "STAFF_ANSWER": "STAFF_ANSWER", "OPERATOR_GUIDE": "OPERATOR_GUIDE",
        "COMMUNITY_OPINION": "COMMUNITY_OPINION", "INFERENCE": "INFERENCE",
    }
    return mapping.get(utype, "CAFE_QA"), "NOT_CHECKED"

def fp(text):
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:16]

def split_inline_qa(body):
    """Split on <질문>/<답변>/[질문]/[답변]/<응답> markers. Returns (lead,[(label,text)]) or None.
    Note: re.split with a capture group interleaves [pre, lab1, seg1, lab2, seg2, ...]."""
    if not QA_MARKER.search(body):
        return None
    parts = QA_MARKER.split(body)
    lead = parts[0].strip()
    chunks = []
    for i in range(1, len(parts), 2):
        lab = parts[i]
        seg = parts[i + 1] if (i + 1) < len(parts) else ""
        chunks.append((lab, clean(seg)))
    return lead, chunks

def process_article(d):
    aid = d["articleId"]
    title = clean(d.get("title") or "")
    body = clean(d.get("bodyNormalized") or "")
    body_len = len(body)
    author = d.get("author") or {}
    a_grade = (author.get("memberGrade") or "").strip()
    a_nick = author.get("nickname") or ""
    comments = d.get("comments") or []
    src_url = d.get("sourceUrl") or ""
    created_ms = d.get("createdAtMs")
    created_iso = d.get("createdAt")
    op_comment_count = d.get("operatorCommentCount") or 0

    units = []
    dropped = []
    seq = [0]

    def emit(utype, text, role, origin, comment_id=None, parent_id=None,
             conf="medium", notes="", c_ms=None, c_iso=None, author_obj=None):
        text = clean(text)
        if not text:
            return None
        seq[0] += 1
        prov, off = prov_and_official(utype)
        u = {
            "unitId": "u-%d-%03d" % (aid, seq[0]),
            "articleId": aid, "boardId": d.get("boardId", 6), "menuId": 6,
            "type": utype, "provenance": prov, "officialCheck": off,
            "tier": tier,
            "role": role, "origin": origin,
            "commentId": comment_id, "parentCommentId": parent_id,
            "author": author_obj if author_obj is not None else {"nickname": a_nick, "memberGrade": a_grade},
            "createdAt": c_iso or created_iso, "createdAtMs": c_ms or created_ms,
            "sourceUrl": src_url,
            "evidenceText": text, "evidenceFingerprint": fp(text),
            "confidence": conf, "needsHumanReview": conf == "low", "notes": notes,
        }
        units.append(u)
        return u

    # ---- image-only: flag, do NOT guess ----
    if aid in IMAGE_ONLY_IDS:
        meta = {"articleId": aid, "status": "IMAGE_ONLY", "needsVisualReview": True,
                "imageCount": len(d.get("images") or []), "tier": None,
                "informational": None, "excluded": False, "reason": "IMAGE_ONLY/NEEDS_VISUAL_REVIEW",
                "unitCount": 0}
        return units, dropped, meta

    is_operator_post = a_grade == OPERATOR_GRADE
    body_case = bool(CASE_MARK.search(body))
    body_reported = is_reported_claim(body)
    has_case_all = body_case or any(CASE_MARK.search(clean(c.get("text") or "")) for c in comments)
    operator_present = is_operator_post or op_comment_count >= 1

    if operator_present:
        tier = 1
    elif body_len >= 300:
        tier = 2
    elif has_case_all:
        tier = 3
    else:
        tier = 4

    informational = bool(Q_MARK.search(body) or body_case or operator_present
                         or body_len >= 80 or len([c for c in comments if clean(c.get('text'))]) >= 2)
    excluded_reason = None if informational else "NOISE/low-information"

    # ---- BODY ----
    split = split_inline_qa(body)
    if is_operator_post:
        if split:
            lead, chunks = split
            for lab, seg in chunks:
                if not seg:
                    continue
                labn = lab.lower()
                if labn == "질문":
                    emit("QUESTION", seg, "OPERATOR", "BODY", conf="high",
                         notes="operator-post inline 질문 (curated Q&A)")
                else:  # 답변 / 응답
                    utype = "COUNSELOR_CLAIM" if is_reported_claim(seg) else "STAFF_ANSWER"
                    emit(utype, seg, "OPERATOR", "BODY", conf="high",
                         notes="operator-post inline 답변" + (" [institution-attributed]" if utype == "COUNSELOR_CLAIM" else ""))
            # leftover lead narration carrying a case (rare) -> OPERATOR_GUIDE
            if lead and (body_case or len(lead) >= 120):
                emit("OPERATOR_GUIDE", lead, "OPERATOR", "BODY", conf="medium",
                     notes="operator-post framing around curated Q&A")
        else:
            # operator-authored post without explicit Q&A markers
            u = emit("OPERATOR_GUIDE", body, "OPERATOR", "BODY", conf="medium",
                     notes=("operator post relaying a case" if (body_case and not body_reported)
                            else "operator guide/instructional post"))
            if u and body_reported:
                u["hasReportedClaim"] = True
                u["notes"] += " | contains institution-attributed claim (verify before citing)"
    else:
        # member author
        if Q_MARK.search((title + " " + body)):
            emit("QUESTION", (title + "\n" + body).strip(), "MEMBER_AUTHOR", "BODY",
                 conf="medium" if body_len >= 40 else "low", notes="member question (title+body)")
        if body_case and (SELF_MARK.search(body) or body_len >= 60):
            emit("ACTUAL_CASE", body, "MEMBER_AUTHOR", "BODY",
                 conf="high" if (SELF_MARK.search(body) and body_len >= 60) else "medium",
                 notes="member narrates own 신청/실효/전환/심사 status")
        if body_reported:
            emit("COUNSELOR_CLAIM", body, "MEMBER_AUTHOR", "BODY", conf="low",
                 notes="institution-attributed claim; NOT official")

    # ---- COMMENTS ----
    thread_asked = bool(Q_MARK.search(body + " " + title))
    for c in comments:
        if c.get("isDeleted"):
            continue
        ctext = clean(c.get("text") or "")
        if not ctext:
            continue
        # drop trivial greeting / emoji / punctuation-only noise -> EXCLUDED
        if TRIVIAL.match(ctext) or THANKS.match(ctext) or len(ctext) < 4:
            dropped.append({"articleId": aid, "commentId": c.get("commentId"),
                            "reason": "trivial/greeting/short", "text": ctext})
            continue
        c_role = "OPERATOR" if c.get("isOperator") else "MEMBER_COMMENTER"
        c_author = {"nickname": c.get("author"), "memberGrade": c.get("memberGrade") or ""}
        kw = dict(comment_id=c.get("commentId"), parent_id=c.get("parentCommentId"),
                  c_ms=c.get("createdAtMs"), c_iso=c.get("createdAt"), author_obj=c_author)
        is_url_only = re.match(r"^https?://\S+$", ctext)

        if c.get("isOperator"):
            utype = "COUNSELOR_CLAIM" if is_reported_claim(ctext) else "STAFF_ANSWER"
            emit(utype, ctext, c_role, "COMMENT", conf="high",
                 notes=("operator answer [institution-attributed]" if utype == "COUNSELOR_CLAIM"
                        else "operator answer comment (reference link)" if is_url_only
                        else "operator answer comment"), **kw)
        else:
            if is_reported_claim(ctext):
                emit("COUNSELOR_CLAIM", ctext, c_role, "COMMENT", conf="low",
                     notes="member reports an institution-attributed claim — NOT official", **kw)
            elif SELF_MARK.search(ctext) and CASE_MARK.search(ctext):
                emit("MEMBER_EXPERIENCE", ctext, c_role, "COMMENT", conf="high",
                     notes="member shares own parallel case/experience", **kw)
            elif ADVICE_MARK.search(ctext) and not SELF_MARK.search(ctext):
                emit("ANSWER", ctext, c_role, "COMMENT",
                     conf="medium" if thread_asked else "low",
                     notes="member advice/answer to thread" + ("" if thread_asked else " (no clear question)"), **kw)
            elif CASE_MARK.search(ctext) and len(ctext) >= 40 and SELF_MARK.search(ctext):
                emit("MEMBER_EXPERIENCE", ctext, c_role, "COMMENT", conf="medium",
                     notes="member experience (case keywords)", **kw)
            else:
                emit("COMMUNITY_OPINION", ctext, c_role, "COMMENT", conf="medium",
                     notes="general member opinion/context", **kw)

    meta = {"articleId": aid, "status": d.get("collectionStatus"), "bodyLen": body_len,
            "commentCount": len(comments), "operatorCommentCount": op_comment_count,
            "tier": tier, "informational": informational,
            "excluded": bool(excluded_reason), "reason": excluded_reason,
            "unitCount": len(units)}
    return units, dropped, meta

def main():
    files = sorted(glob.glob(os.path.join(ARTICLES, "article_*.json")),
                   key=lambda p: int(re.search(r"article_(\d+)", p).group(1)))
    all_units, all_dropped, article_meta = [], [], []
    type_counts, tiers = {}, {}
    excluded, image_only, articles_with_units = [], [], 0
    for f in files:
        d = json.load(open(f, encoding="utf-8"))
        units, dropped, meta = process_article(d)
        all_units.extend(units)
        all_dropped.extend(dropped)
        article_meta.append(meta)
        if meta.get("excluded"):
            excluded.append(meta["articleId"])
        if meta.get("reason") == "IMAGE_ONLY/NEEDS_VISUAL_REVIEW":
            image_only.append(meta["articleId"])
        if units:
            articles_with_units += 1
        for u in units:
            type_counts[u["type"]] = type_counts.get(u["type"], 0) + 1

    # cross-unit dedup on (articleId,type,fingerprint)
    seen, deduped, dupes = {}, [], 0
    for u in all_units:
        key = (u["articleId"], u["type"], u["evidenceFingerprint"])
        if key in seen:
            dupes += 1
            continue
        seen[key] = True
        deduped.append(u)
    for u in deduped:
        t = u.get("tier")
        tiers[t] = tiers.get(t, 0) + 1

    out = {
        "phase": "PHASE1-STEP2",
        "board": {"boardId": 6, "menuId": 6, "name": "부실우려", "cafeKey": "newhopero", "cafeClubId": 31312978},
        "generator": "build_qa_units.py (v2)",
        "inputRaw": 248,
        "rule": ("deterministic first-pass; officialCheck=NOT_CHECKED (no cafe->official promotion); "
                 "COUNSELOR_CLAIM only on institution-attributed hearsay; greeting/emoji units dropped; "
                 "INFERENCE not fabricated"),
        "stats": {
            "articlesTotal": len(files),
            "articlesWithUnits": articles_with_units,
            "unitsEmitted": len(deduped),
            "unitsBeforeDedup": len(all_units),
            "intraArticleDupesRemoved": dupes,
            "noiseUnitsDropped": len(all_dropped),
            "byType": type_counts,
            "byTier": {str(k): v for k, v in sorted((x for x in tiers.items() if x[0] is not None),
                                                     key=lambda z: str(z[0]))},
            "imageOnly": sorted(image_only),
            "excludedArticles": sorted(excluded),
        },
        "units": deduped,
        "droppedNoise": all_dropped,
        "articleMeta": article_meta,
    }
    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("WROTE", OUT)
    print("units:", len(deduped), "before dedup:", len(all_units), "crossdupes:", dupes,
          "noiseDropped:", len(all_dropped))
    print("byType:", json.dumps(type_counts, ensure_ascii=False))
    print("byTier:", json.dumps({k: tiers[k] for k in sorted(tiers) if k is not None}, ensure_ascii=False))
    print("articlesWithUnits:", articles_with_units, "/ 248")
    print("imageOnly:", sorted(image_only), "excludedArticles:", len(excluded))

if __name__ == "__main__":
    main()
