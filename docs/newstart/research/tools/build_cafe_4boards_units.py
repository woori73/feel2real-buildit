#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""4개 게시판 RAW -> qa_units.json + load_batches/ (DB 반영 payload 계약).

unit 분류 로직은 부실우려(b6) PHASE1 STEP2에서 검증된 `build_qa_units.process_article`를
그대로 재사용한다(규칙 복제 X, import 재사용). b6 스크립트는 menuId=6을 하드코딩하므로
unit의 menuId/boardId만 기사 값으로 교정한다.

payload 14필드 계약(sourceType,category,title,summary,content,authority,tags,sourceUrl,
sourceDate,effectiveFrom,effectiveTo,confidence,relatedCaseIds,relatedRuleIds)은
`load_cafe_qa_prod.py`가 prod에서 exact-match 스킵에 쓰는 시그니처와 동일하다.

과거 임시안 게시판(menuId=4) units는 tags에 `historicalCANDIDATE=true`를 붙이고
effectiveFrom/effectiveTo는 임의로 채우지 않는다(판정은 사람).
"""
from __future__ import annotations

import collections
import datetime as dt
import hashlib
import importlib.util
import json
import os
import pathlib
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.dirname(HERE)
PKG = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(
    os.path.join(BASE, "cafe-4boards-20261004"))

spec = importlib.util.spec_from_file_location("b6_units", os.path.join(HERE, "build_qa_units.py"))
b6 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b6)

BOARDS = {3: "부실차주 신청", 4: "부실차주 임시안부터 확정안",
          5: "재취업 재창업 감면수익+정부 지원", 9: "하랑랑 스페셜"}
LABEL = {
    "QUESTION": "실제로 많이 묻는 질문", "ANSWER": "회원 답변",
    "ACTUAL_CASE": "실제 신청·체결·전환 사례", "MEMBER_EXPERIENCE": "회원 경험담",
    "COUNSELOR_CLAIM": "상담사 주장(확인 필요)", "STAFF_ANSWER": "운영자 답변",
    "OPERATOR_GUIDE": "운영자 안내", "COMMUNITY_OPINION": "커뮤니티 의견",
    "INFERENCE": "추론(검토 필요)",
}
ORDER = ["ACTUAL_CASE", "OPERATOR_GUIDE", "STAFF_ANSWER", "ANSWER",
         "MEMBER_EXPERIENCE", "QUESTION", "COUNSELOR_CLAIM", "COMMUNITY_OPINION", "INFERENCE"]
OPERATOR_ROLES = {"OPERATOR"}
RANK = {t: i for i, t in enumerate(ORDER)}
BATCH_SIZE = 100


def fp(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]


def source_type_for(u: dict) -> str:
    if u["type"] == "ACTUAL_CASE":
        return "CASE"
    if u.get("origin") == "COMMENT":
        return "CAFE_COMMENT"
    return "CAFE_ARTICLE"


def main() -> int:
    articles_dir = PKG / "articles"
    units, dropped, metas, scope_dropped = [], [], [], []
    for path in sorted(articles_dir.glob("article_*.json")):
        d = json.loads(path.read_text(encoding="utf-8"))
        menu = d.get("boardId") or 0
        us, drs, meta = b6.process_article(d)
        for u in us:
            u["menuId"] = menu
            u["boardId"] = menu
            u["boardName"] = BOARDS.get(menu)
            u["unitId"] = "m%d-%s" % (menu, u["unitId"])
            # 운영자(agent) 계정 게시글의 본문 단위와 그 댓글의 운영자 단위는 수집 범위에서 제외한다.
            # 근거: NewStart 수집 범위 = 회원 정보글만 취합(댓글 내 운영자 답변은 사례 판별 근거로 보존).
            if d.get("operatorAuthored") and (u.get("origin") == "BODY" or u.get("role") in OPERATOR_ROLES):
                scope_dropped.append({"articleId": u["articleId"], "unitId": u["unitId"],
                                      "type": u["type"], "origin": u.get("origin"),
                                      "reason": "OPERATOR_AGENT_POST_BODY" if u.get("origin") == "BODY"
                                      else "OPERATOR_AGENT_COMMENT",
                                      "evidenceText": (u.get("evidenceText") or "")[:160]})
                continue
            units.append(u)
        for x in drs:
            x["boardId"] = menu
            dropped.append(x)
        meta["boardId"] = menu
        meta["boardName"] = BOARDS.get(menu)
        meta["operatorAuthored"] = bool(d.get("operatorAuthored"))
        metas.append(meta)

    out_units = {
        "package": "cafe-4boards-20261004",
        "boards": {str(k): v for k, v in BOARDS.items()},
        "generator": "build_cafe_4boards_units.py (reuses build_qa_units.process_article)",
        "rule": "deterministic first-pass; officialCheck=NOT_CHECKED (no cafe->official promotion); "
                "greeting/emoji units dropped; INFERENCE not fabricated; "
                "menuId=4 units tagged historicalCANDIDATE (과거 임시안, current 판정 안 함); "
                "운영자(agent) 계정 게시글의 본문·운영자 단위 제외(회원 정보글만 취합)",
        "stats": {
            "articlesTotal": len(metas),
            "articlesWithUnits": len({u["articleId"] for u in units}),
            "unitsEmitted": len(units),
            "noiseUnitsDropped": len(dropped),
            "operatorScopeUnitsDropped": len(scope_dropped),
            "operatorAuthoredArticles": sum(1 for m in metas if m.get("operatorAuthored")),
            "byType": {},
            "byBoard": {},
        },
        "units": units,
        "dropped": dropped,
        "scopeDropped": scope_dropped,
        "articleMeta": metas,
    }
    for u in units:
        out_units["stats"]["byType"][u["type"]] = out_units["stats"]["byType"].get(u["type"], 0) + 1
        k = "m%d" % (u["menuId"] or 0)
        out_units["stats"]["byBoard"][k] = out_units["stats"]["byBoard"].get(k, 0) + 1
    (PKG / "qa_units.json").write_text(json.dumps(out_units, ensure_ascii=False, indent=1), encoding="utf-8")

    rows = []
    for u in units:
        if u.get("type") == "INFERENCE":
            continue
        aid = u["articleId"]
        menu = u["menuId"]
        label = LABEL.get(u["type"], u["type"])
        ev = (u.get("evidenceText") or "").strip()
        if not ev:
            continue
        # 같은 글에 identical 댓글이 존재하면(감사인사 등) 본문 지문만으로는 ck가 충돌한다.
        # dedup_key는 UNIQUE 제약이므로 댓글 id를 스코프로 넣어 idempotency를 보장한다.
        scope = ("c%s" % u["commentId"]) if (u.get("origin") == "COMMENT" and u.get("commentId")) else "body"
        dkey = "CAFE4B:m%d:%d:%s:%s:%s" % (menu, aid, u["type"], scope, fp(ev))
        hist = ",historicalCANDIDATE=true" if menu == 4 else ""
        rows.append({
            "rank": (RANK.get(u["type"], 99), menu, aid),
            "req": {
                "sourceType": source_type_for(u),
                "category": BOARDS.get(menu),
                "title": ("[카페 4boards m%d#%d·%s] %s" % (menu, aid, label, ev[:40]))[:200],
                "summary": "출처 article %d (%s) · %s · 공식확인 전(NOT_CHECKED)%s"
                           % (aid, BOARDS.get(menu), label, " · 과거 임시안 후보" if menu == 4 else ""),
                "content": ev[:600],
                "authority": "%s 카페 4boards REVIEW 적재(2026-10-04)" % BOARDS.get(menu),
                "tags": ("CAFE_4BOARDS,m%d,menuId%d,unit=%s,officialCheck=NOT_CHECKED%s,ck=%s"
                         % (menu, menu, u["type"], hist, dkey)),
                "sourceUrl": u.get("sourceUrl"),
                "sourceDate": (u.get("createdAt") or "")[:10] or None,
                "confidence": u.get("confidence"),
            },
        })
    rows.sort(key=lambda r: r["rank"])
    payloads = [r["req"] for r in rows]
    keys = [r["tags"].split("ck=")[-1] for r in payloads]
    if len(keys) != len(set(keys)):
        dupes = [k for k, c in collections.Counter(keys).items() if c > 1]
        raise SystemExit("DUPLICATE dedup_key: %s" % dupes[:5])
    bdir = PKG / "load_batches"
    bdir.mkdir(exist_ok=True)
    batches = [payloads[i:i + BATCH_SIZE] for i in range(0, len(payloads), BATCH_SIZE)]
    for i, b in enumerate(batches):
        (bdir / ("batch_%02d.json" % i)).write_text(
            json.dumps(b, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    manifest = {
        "package": "cafe-4boards-20261004",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "totalToLoad": len(payloads),
        "batchSize": BATCH_SIZE,
        "batchCount": len(batches),
        "orderNote": "menuId3(신청)→4(임시안~확정안)→5(재취업·재창업) 순, 각 게시판 내 ACTUAL_CASE/OPERATOR_GUIDE/"
                     "STAFF_ANSWER/ANSWER/MEMBER_EXPERIENCE/QUESTION/COUNSELOR_CLAIM 우선, COMMUNITY_OPINION 마지막",
        "batches": [{"file": "batch_%02d.json" % i, "count": len(b),
                     "ck_first": json.loads((bdir / ("batch_%02d.json" % i)).read_text(encoding="utf-8"))[0]["tags"]}
                    for i, b in enumerate(batches)],
        "perBoard": {},
    }
    for r in rows:
        m = r["req"]["category"]
        manifest["perBoard"][m] = manifest["perBoard"].get(m, 0) + 1
    (bdir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")

    print("UNITS=%d PAYLOADS=%d BATCHES=%d" % (len(units), len(payloads), len(batches)))
    print("OPERATOR_SCOPE_DROPPED=%d (agent posts %d건)" % (len(scope_dropped),
                                                            sum(1 for m in metas if m.get("operatorAuthored"))))
    print("BY_BOARD " + " ".join("%s=%s" % (k, v) for k, v in out_units["stats"]["byBoard"].items()))
    print("DROPPED_NOISE=%d" % len(dropped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
