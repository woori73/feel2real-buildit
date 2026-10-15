#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PHASE1 STEP3 DEDUPE — build stable dedup keys, normalized titles, content fingerprints
and same-event relations for the 248 board6 (부실우려) qa_units. Reuses EXISTING pipeline
semantics; does NOT invent a new framework.

Inputs : qa_units.json (STEP2), articles/article_*.json, INDEX.json
Output : cafe_dedup_report.json

Idempotency contract mirrored from the running app:
- newstart_empirical_cases.idempotent by article_id (UNIQUE)  -> ACTUAL_CASE maps to a case key.
- newstart_knowledge_items.idempotent by title (findByTitle) + optional UNIQUE dedup_key (V1012).
  We mint a stable dedup_key per unit so re-ingest never duplicates a knowledge row.
Existing data is NEVER deleted or overwritten; only new Q&A / comments / cases / relations
are added incrementally. Article-level overlap vs committed ingested boards is reported.
"""
import json, os, re, glob, hashlib, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.normpath(os.path.join(HERE, "..", "cafe-qa-phase1-b6"))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))  # repo root
UNITS = json.load(open(os.path.join(DATA, "qa_units.json"), encoding="utf-8"))

# articleId -> title (from RAW articles), used for stable normalized knowledge titles
UNITS_TITLE = {}
for _f in glob.glob(os.path.join(DATA, "articles", "article_*.json")):
    _d = json.load(open(_f, encoding="utf-8"))
    UNITS_TITLE[_d["articleId"]] = _d.get("title") or ""

def norm_title(s):
    s = (s or "").replace("​", "").strip()
    s = re.sub(r"\s+", " ", s)
    s = "".join(ch for ch in unicodedata.normalize("NFKC", s))
    return s

def fp12(s):
    return hashlib.sha1((s or "").encode("utf-8")).hexdigest()[:12]

def sha256(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()

# normalized Korean display label per internal type (rule #12: never expose raw enum to users)
TYPE_KOR = {
    "QUESTION": "실제로 많이 묻는 질문",
    "ANSWER": "회원 답변",
    "ACTUAL_CASE": "실제 상담·신청 사례",
    "MEMBER_EXPERIENCE": "카페 회원 경험",
    "COUNSELOR_CLAIM": "상담 과정에서 안내받은 내용",
    "STAFF_ANSWER": "운영자 안내",
    "OPERATOR_GUIDE": "운영자 안내",
    "COMMUNITY_OPINION": "회원 의견",
    "INFERENCE": "종합 판단(검토 필요)",
}
# internal provenance -> DB SourceType (reuse existing enum values)
PROV_TO_SOURCETYPE = {
    "CAFE_QA": "CAFE_ARTICLE",        # overridden to CAFE_COMMENT for origin==COMMENT below
    "CAFE_CASE": "CASE",
    "MEMBER_EXPERIENCE": "CAFE_COMMENT",
    "COUNSELOR_CLAIM": "CAFE_COMMENT",
    "STAFF_ANSWER": "OPERATOR_KNOWLEDGE",
    "OPERATOR_GUIDE": "OPERATOR_KNOWLEDGE",
    "COMMUNITY_OPINION": "CAFE_COMMENT",
    "INFERENCE": "DERIVED_RULE",
}

def source_type_for(u):
    if u["origin"] == "COMMENT":
        if u["type"] in ("STAFF_ANSWER",):
            return "OPERATOR_KNOWLEDGE"
        return "CAFE_COMMENT"
    # BODY
    if u["type"] == "ACTUAL_CASE":
        return "CASE"
    if u["type"] in ("STAFF_ANSWER", "OPERATOR_GUIDE"):
        return "OPERATOR_KNOWLEDGE"
    return "CAFE_ARTICLE"

units = UNITS["units"]
title_by_article = {}
for u in UNITS["articleMeta"]:
    pass

dedup_rows = []
seen_keys = {}
collisions_within_batch = 0
for u in units:
    aid = u["articleId"]
    ntitle = norm_title(u["author"]["nickname"] or "")
    body = u["evidenceText"]
    # normalized title uses article title + type (title lives in unit sourceUrl; fetch from RAW)
    art_title = UNITS_TITLE.get(aid, "")
    normT = norm_title(art_title)
    content_fp = fp12(norm_title(body))
    dedup_key = "CAFE_QA:b6:%d:%s:%s" % (aid, u["type"], content_fp)
    if dedup_key in seen_keys:
        collisions_within_batch += 1
    seen_keys[dedup_key] = True
    display_title = "[카페Q&A b6#%d·%s] %s" % (aid, TYPE_KOR.get(u["type"], u["type"]),
                                               (normT[:60] if normT else body[:40]))
    dedup_rows.append({
        "unitId": u["unitId"],
        "articleId": aid,
        "type": u["type"],
        "sourceType_db": source_type_for(u),
        "dedup_key": dedup_key,
        "knowledge_title_idempotent": display_title[:200],
        "contentFingerprint": sha256(body)[:32],
        "empirical_case_article_key": ("CASE:b6:%d" % aid) if u["type"] == "ACTUAL_CASE" else None,
        "officialCheck": u["officialCheck"],
        "sourceUrl": u["sourceUrl"],
        "date": u["createdAt"],
        "authorType": u["role"],
    })

# ---- relations (same-event Q -> A -> followup), knowledge<->knowledge edges ----
# group units by article; link QUESTION -> ANSWER/STAFF_ANSWER; ACTUAL_CASE -> MEMBER_EXPERIENCE(followup)
by_article = {}
for u in units:
    by_article.setdefault(u["articleId"], []).append(u)

relations = []
for aid, us in sorted(by_article.items()):
    qs = [x for x in us if x["type"] == "QUESTION"]
    ans = [x for x in us if x["type"] in ("ANSWER", "STAFF_ANSWER", "OPERATOR_GUIDE")]
    case = [x for x in us if x["type"] == "ACTUAL_CASE"]
    exp = [x for x in us if x["type"] == "MEMBER_EXPERIENCE"]
    for q in qs:
        for a in ans:
            relations.append({"from": q["unitId"], "to": a["unitId"],
                              "relationType": "CASE", "articleId": aid,
                              "note": "question->answer within thread"})
    for c in case:
        for e in exp:
            relations.append({"from": c["unitId"], "to": e["unitId"],
                              "relationType": "PREREQUISITE", "articleId": aid,
                              "note": "case->member follow-up/experience in same thread"})
    # CONTRADICTS only where a COUNSELOR_CLAIM directly conflicts an operator answer — conservative:
    # we DO NOT auto-assert contradiction (needs human verify). Left empty on purpose.

# ---- article-level overlap vs committed ingested boards ----
def ids_from_glob(pat, idgroup=1):
    s = set()
    for f in glob_files(pat):
        m = re.search(r"(\d+)", os.path.basename(f))
        if m:
            s.add(int(m.group(idgroup)))
    return s

import glob as _g
def glob_files(pat):
    return _g.glob(os.path.join(ROOT, pat))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))  # repo root
b6 = set(a["articleId"] for a in UNITS["articleMeta"])
canon = set(int(re.search(r"article_(\d+)", f).group(1)) for f in glob_files("docs/newstart/research/article_json/article_*.json"))
gam = set(int(re.search(r"(\d+)", os.path.basename(f)).group(1)) for f in glob_files("data/newstart-cafe/gamian/articles/*.json") if re.search(r"(\d+)", os.path.basename(f)))

overlap = {
    "b6_total": len(b6),
    "b6_intersect_canonical_empirical_board": sorted(b6 & canon),
    "b6_intersect_gamian_board22": sorted(b6 & gam),
    "article_5869_in_b6": 5869 in b6,
    "image_only_excluded": UNITS["stats"]["imageOnly"],
    "intra_batch_dedupkey_collisions": collisions_within_batch,
    "note": ("article-level overlap vs committed ingested boards = 0; authoritative prod-collision "
             "prevented at write time by dedup_key UNIQUE + empirical_cases.article_id UNIQUE + findByTitle. "
             "Optional single read-only census run can confirm live delta before dry_run=false."),
}

report = {
    "phase": "PHASE1-STEP3",
    "board": UNITS["board"],
    "generatedBy": "build_cafe_dedup.py",
    "idempotency": {
        "knowledge_items": "dedup_key UNIQUE (V1012) + findByTitle; status forced REVIEW",
        "empirical_cases": "article_id UNIQUE",
        "no_delete_overwrite": True,
    },
    "dedupRows": dedup_rows,
    "relations": relations,
    "overlap": overlap,
    "summary": {
        "units": len(dedup_rows),
        "unique_dedup_keys": len(seen_keys),
        "relations_emitted": len(relations),
        "contradicts_auto_asserted": 0,
    },
}
OUTP = os.path.join(DATA, "cafe_dedup_report.json")
json.dump(report, open(OUTP, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("WROTE", OUTP)
print("units", len(dedup_rows), "unique_keys", len(seen_keys),
      "intra_collisions", collisions_within_batch, "relations", len(relations))
print("overlap b6∩canon", len(b6 & canon), "b6∩gamian", len(b6 & gam), "5869_in_b6", 5869 in b6)

# ---- write DB-ready payload for the EXISTING review pipeline (no new framework) ----
payload = {
    "batchId": "cafe-qa-b6-2026-09-30",
    "menuId": 6,
    "boardName": "부실우려",
    "status": "REVIEW",           # ingest forces REVIEW; never ACTIVE here
    "officialCheck": "NOT_CHECKED",
    "items": [
        {
            "dedupKey": d["dedup_key"],
            "title": d["knowledge_title_idempotent"],
            "sourceType": d["sourceType_db"],
            "category": "부실우려",
            "articleId": d["articleId"],
            "unitType": d["type"],
            "displayLabel": TYPE_KOR.get(d["type"], d["type"]),  # human Korean, no raw enum
            "contentFingerprint": d["contentFingerprint"],
            "sourceUrl": d["sourceUrl"],
            "sourceDate": d["date"],
            "authorType": d["authorType"],
            "confidence": next((u["confidence"] for u in units if u["unitId"] == d["unitId"]), "medium"),
        } for d in dedup_rows
    ],
    "relations": relations,
    "empirical_case_keys": [d["empirical_case_article_key"] for d in dedup_rows if d["empirical_case_article_key"]],
}
POUT = os.path.join(DATA, "cafe_qa_db_ready.json")
json.dump(payload, open(POUT, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("WROTE", POUT, "items", len(payload["items"]), "case_keys", len(payload["empirical_case_keys"]))
