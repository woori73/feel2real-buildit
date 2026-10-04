#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Library 전달파일 2종을 보존 RAW(article_json) + qa_units에서 재생성한다.

- newstart_cafe_4boards_2026-10-04_raw.json : 원본 보존 레이어(전문·댓글·unit)
- newstart_cafe_4boards_2026-10-04_library.md : 사람이/Agent가 읽는 지식 전달본

Qoder는 파일 제작까지만 수행한다(ChatGPT Library 업로드 없음).
본문·댓글은 요약·절단하지 않고 전문을 싣는다(지시 §10: 경험·절차·결과 과축약 금지).
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import pathlib
import re

BOARD_ORDER = [3, 4, 5, 9]
BOARD_TITLE = {
    3: "부실차주 신청",
    4: "부실차주 임시안부터 확정안 (과거 자료)",
    5: "재취업·재창업 감면수익 + 정부 지원",
    9: "하랑랑 스페셜",
}
LABEL = {
    "QUESTION": "자주 묻는 질문", "ANSWER": "회원 답변",
    "ACTUAL_CASE": "실제 신청·체결·전환 사례", "MEMBER_EXPERIENCE": "회원 경험담",
    "COUNSELOR_CLAIM": "상담 과정에서 안내받은 내용", "STAFF_ANSWER": "운영자 답변",
    "OPERATOR_GUIDE": "운영자 안내", "COMMUNITY_OPINION": "커뮤니티 의견",
    "INFERENCE": "추론(검토 필요)",
}
TYPE_ORDER = ["ACTUAL_CASE", "OPERATOR_GUIDE", "STAFF_ANSWER", "ANSWER",
              "MEMBER_EXPERIENCE", "QUESTION", "COUNSELOR_CLAIM", "COMMUNITY_OPINION"]

# 전달본에서만 마스킹하는 개인정보 패턴(저장소 원문 보존본은 손대지 않는다).
# 금액(10^9 이상)과 오검출되지 않도록 구분자 있는 전화번호·계좌·카드 패턴만 사용한다.
PII = [
    (r"\b01[016789]-?\d{3,4}-?\d{4}\b", "010-xxxx-xxxx"),
    (r"\b02-?\d{3,4}-?\d{4}\b", "02-xxx-xxxx"),
    (r"\b\d{2,3}-\d{5,6}-\d{2,3}\b", "xxx-xxxxxx-xxx"),      # 은행 계좌번호
    (r"\b\d{4}-\d{4}-\d{4}-\d{4}\b", "xxxx-xxxx-xxxx-xxxx"),  # 카드번호
    (r"\b\d{2,3}-\d{2}-\d{6}\b", "xxx-xx-xxxxxx"),            # 주민등록번호
    (r"\b\d{6}-\d{2}-\d{6}\b", "xxxxxx-xx-xxxxxx"),
]


def mask(s: str) -> str:
    out = s or ""
    for pat, rep in PII:
        out = re.sub(pat, rep, out)
    return out


def masked_copy(recs):
    """전달본 사본만 마스킹한다(저장소 RAW는 원문 보존)."""
    out = []
    for r in recs:
        c = json.loads(json.dumps(r, ensure_ascii=False))
        c["title"] = mask(c["title"])
        c["body"] = mask(c["body"])
        if isinstance(c.get("bodyRaw"), list):
            c["bodyRaw"] = [mask(x) for x in c["bodyRaw"]]
        for cm in c["comments"]:
            cm["text"] = mask(cm.get("text"))
        for u in c["knowledgeUnits"]:
            u["evidenceText"] = mask(u.get("evidenceText"))
        out.append(c)
    return out


def load(pkg: pathlib.Path):
    arts = {}
    for f in (pkg / "articles").glob("article_*.json"):
        o = json.loads(f.read_text(encoding="utf-8"))
        arts[int(o["articleId"])] = o
    units = json.loads((pkg / "qa_units.json").read_text(encoding="utf-8"))
    index = json.loads((pkg / "INDEX.json").read_text(encoding="utf-8"))
    dedup = json.loads((pkg / "cafe_4boards_dedup_report.json").read_text(encoding="utf-8"))
    by_article = collections.defaultdict(list)
    for u in units["units"]:
        by_article[int(u["articleId"])].append(u)
    return arts, units, index, dedup, by_article


def article_records(arts, by_article, index):
    recs = []
    for aid in sorted(arts):
        a = arts[aid]
        menu = int(a["boardId"])
        us = sorted(by_article.get(aid, []),
                    key=lambda u: (TYPE_ORDER.index(u["type"]) if u["type"] in TYPE_ORDER else 99))
        det = index["boards_detail"].get(str(menu), {})
        recs.append({
            "menuId": menu,
            "boardName": a["boardName"],
            "boardNote": a.get("boardNote"),
            "articleId": aid,
            "title": a["title"],
            "author": a["author"],
            "writtenAt": a["createdAt"],
            "writtenAtRaw": a.get("writtenAtRaw"),
            "viewCount": a.get("viewCount"),
            "commentCountDeclared": a.get("commentCount"),
            "commentsCaptured": a.get("commentsCaptured"),
            "sourceUrl": a["sourceUrl"],
            "collectedAt": a.get("collectedAt"),
            "collectionStatus": a.get("collectionStatus"),
            "parseWarnings": a.get("parseWarnings") or [],
            "classification": {
                "historical": bool(a.get("historicalCandidate")),
                "duplicate": False,
                "informational": a.get("collectionStatus") != "EMPTY_BODY" or bool(a.get("images")),
                "officialCheck": "NOT_CHECKED",
                "origin": "CAFE_OPERATOR_AGENT_POST" if a.get("operatorAuthored") else "CAFE_MEMBER",
                "collectionScope": ("IN_SCOPE_BY_USER_RULING_2026-10-04"
                                    if a.get("scopeIncludedByUserRuling") else
                                    ("EXCLUDED_OPERATOR_AGENT_POST" if a.get("operatorAuthored") else "IN_SCOPE")),
            },
            "topicTags": a.get("topicTags"),
            "operatorAuthored": bool(a.get("operatorAuthored")),
            "scopeExcluded": a.get("scopeExcluded"),
            "scopeIncludedByUserRuling": a.get("scopeIncludedByUserRuling"),
            "contentForm": a.get("contentForm"),
            "images": a.get("images") or [],
            "links": a.get("links") or [],
            "boardNote": a.get("boardNote"),
            "bodyRaw": a.get("bodyRaw"),
            "body": a.get("bodyNormalized") or "",
            "attachments": a.get("attachments") or [],
            "comments": a.get("comments") or [],
            "knowledgeUnits": [
                {
                    "unitId": u["unitId"], "type": u["type"], "label": LABEL.get(u["type"], u["type"]),
                    "role": u.get("role"), "origin": u.get("origin"), "commentId": u.get("commentId"),
                    "evidenceText": u.get("evidenceText"), "tier": u.get("tier"),
                    "confidence": u.get("confidence"), "officialCheck": u.get("officialCheck"),
                } for u in us
            ],
            "boardSurveyed": det.get("surveyed"),
        })
    return recs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pkg", help="docs/newstart/research/cafe-4boards-20261004")
    ap.add_argument("--outdir", action="append", default=[], help="추가 출력 디렉토리(반복 가능)")
    ap.add_argument("--db-note", default=None,
                    help="운영 DB 반영 결과를 전달본 헤더에 기록(미지정 시 미반영으로 표기)")
    args = ap.parse_args()

    pkg = pathlib.Path(args.pkg)
    arts, units, index, dedup, by_article = load(pkg)
    recs = article_records(arts, by_article, index)
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
    # 운영 DB 반영 대장이 있으면 전달본 헤더에 실제 결과를 기록한다(없으면 미반영 표기).
    db_file = pkg / "db_reflect_2026-10-04.json"
    db = json.loads(db_file.read_text(encoding="utf-8")) if db_file.exists() else None
    dbc = (db or {}).get("counts", {})

    # ---------- raw json ----------
    raw = {
        "dataset": "newstart_cafe_4boards",
        "version": "2026-10-04",
        "generatedAt": now,
        "task": "NewStart 부실차주 카페 과거 게시판 4곳 추가 수집 (menuId 3/4/5/9)",
        "cafe": {"name": "새출발기금 부실차주 라이브러리", "cafeKey": "newhopero",
                 "clubId": 31312978, "url": "https://cafe.naver.com/newhopero"},
        "site": "https://feel2real.co.kr/newstart",
        "usagePolicy": {
            "memberExperienceIsNotOfficialRule": True,
            "officialCheck": "NOT_CHECKED",
            "historicalBoardMenuId4": "임시안~확정안 게시판은 역사 자료. 현재 시행 규정으로 제시 금지",
            "separation": "공식자료 / 회원경험 / 과거정책을 구분해 사용할 것",
            "productionDbWrite": (args.db_note if args.db_note else (
                "운영 DB 반영 완료(2026-10-04): %d행 insert-only DRAFT→REVIEW, ACTIVE 승인 %d행, "
                "근거 불완전 %d행은 REVIEW 보류. 직접 SQL·기존 ACTIVE 수정 없음"
                % (dbc.get("newlyInserted", 0), dbc.get("approvedActive", 0), dbc.get("heldAtReview", 0))
                if db else "전달본만 제작 — 운영 DB 미반영(DRY_RUN에서도 아직 실행 안 함)")),
            "deliveryMasking": "전화·계좌·카드·주민 패턴은 전달본에서만 마스킹했다. 저장소 원문 보존본은 무마스크.",
        },
        "boards": [
            {"menuId": m, "boardName": BOARD_TITLE[m], "surveyed": index["boards_detail"][str(m)]["surveyed"],
             "existingDuplicate": index["boards_detail"][str(m)]["existingDuplicateDeclared"],
             "greetingExcluded": index["boards_detail"][str(m)]["greetingExcludedDeclared"],
             "operatorInfoPostsFound": index["boards_detail"][str(m)].get("operatorInfoPostsDeclared", 0),
             # 발견한 운영자 글 중 지시 보존(수집)분을 뺀 값만 제외로 센다.
             "operatorInfoExcluded": max(index["boards_detail"][str(m)].get("operatorInfoPostsDeclared", 0)
                                         - sum(1 for r in recs if r["menuId"] == m
                                               and r.get("scopeIncludedByUserRuling")), 0),
             "collected": sum(1 for r in recs if r["menuId"] == m),
             "operatorPosts": sum(1 for r in recs if r["menuId"] == m
                                  and r["classification"]["origin"] == "CAFE_OPERATOR_AGENT_POST"),
             "operatorPostsKeptByRuling": sum(1 for r in recs if r["menuId"] == m
                                              and r.get("scopeIncludedByUserRuling")),
             "operatorPostsExcluded": sum(1 for r in recs if r["menuId"] == m
                                          and r["classification"]["collectionScope"]
                                          == "EXCLUDED_OPERATOR_AGENT_POST"),
             "postsInScope": sum(1 for r in recs if r["menuId"] == m
                                 and r["classification"]["collectionScope"]
                                 != "EXCLUDED_OPERATOR_AGENT_POST"),
             "informational": sum(1 for r in recs if r["menuId"] == m and r["classification"]["informational"]),
             "units": sum(len(r["knowledgeUnits"]) for r in recs if r["menuId"] == m),
             "historical": m == 4,
             "scopeNote": index["boards_detail"][str(m)].get("scopeNote")}
            for m in BOARD_ORDER
        ],
        "counts": {
            "articles": len(recs),
            "comments": sum(len(r["comments"]) for r in recs),
            "knowledgeUnits": sum(len(r["knowledgeUnits"]) for r in recs),
            "noiseUnitsDropped": units["stats"]["noiseUnitsDropped"],
            "operatorScopeUnitsDropped": units["stats"].get("operatorScopeUnitsDropped", 0),
            "operatorPostsKeptByRuling": sum(1 for r in recs if r.get("scopeIncludedByUserRuling")),
            "operatorKeptUnits": units["stats"].get("operatorKeptUnits", 0),
            "imageOnlyArticles": sum(1 for r in recs if r.get("contentForm") == "IMAGE_ONLY"),
            "baselineArticleIds": dedup["article_level"]["baseline_total"],
            "overlapWithBaseline": dedup["article_level"]["overlap_with_baseline"],
        },
        "productionReflection": ({
            "executedAt": db["executedAt"],
            "channel": db["executionChannel"]["how"],
            "endpoints": db["executionChannel"]["endpoints"],
            "notUsed": db["executionChannel"]["notUsed"],
            "counts": dbc,
            "perBoard": db["perBoard"],
            "holdReasons": db["holdReasons"],
            "knowledgeIdRange": db["knowledgeIdRange"],
            "disclosures": db["defectsAndDisclosures"],
        } if db else {"status": "NOT_REFLECTED"}),
        "userRuling20261004": {
            "menuId9": "하랑랑 스페셜 32건 중 가입인사·등업 17건은 제외 유지, "
                       "운영자(지피지기자산·등급 999) 정보글 15건은 중요자료로 전원 보존·반영 후보 포함. "
                       "다른 게시판의 운영자 본문 단위는 종전 제외 범위 유지.",
            "harangRejected": index["boards_detail"]["9"]["greetingExcludedDeclared"],
            "harangOperatorKept": index["boards_detail"]["9"]["operatorInfoPostsDeclared"],
            "reCollectionNote": "15건은 1차 tranch에서 전문이 보존되지 않고 목록 메타만 보존되어 있어, "
                                "본문·이미지 URL·댓글 증거를 확보하기 위해 15건만 재접속 수집했다(4개 게시판 재수집 아님).",
            "activeGate": "15건은 본문이 이미지/링크 미리보기로만 구성되어 근거 텍스트가 약하다. "
                          "지시에 따라 억지로 ACTIVE 처리하지 않고 REVIEW에서 근거 보류를 남긴다.",
        },
        "excludedFromMenuId9": {
            "greetingOrLevelUp": index["boards_detail"]["9"]["greetingExcludedDeclared"],
            "reason": index["boards_detail"]["9"]["collectionExcluded"],
        },
        "articles": masked_copy(recs),
    }
    raw_name = "newstart_cafe_4boards_2026-10-04_raw.json"
    md_name = "newstart_cafe_4boards_2026-10-04_library.md"
    dests = [pkg / "delivery"] + [pathlib.Path(d) for d in args.outdir]
    for d in dests:
        d.mkdir(parents=True, exist_ok=True)
        (d / raw_name).write_text(json.dumps(raw, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    # ---------- library md ----------
    L = []
    L.append("# 새출발기금 부실차주 라이브러리 — 4개 게시판 수집본 (2026-10-04)\n")
    L.append("카페: 새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero) · "
             "대상 게시판: 부실차주 신청 / 부실차주 임시안부터 확정안 / 재취업·재창업 감면수익+정부 지원 / 하랑랑 스페셜\n")
    L.append("## 이 자료를 쓸 때 지켜야 할 세 가지")
    L.append("")
    L.append("1. 아래 글은 **카페 회원이 직접 남긴 경험과 커뮤니티 안내**입니다. 회원 경험을 공식 규정처럼 제시하지 마세요 "
             "(공식 확인 전 = NOT_CHECKED). 운영자(카페매니저·등급 999) 계정이 작성한 게시글 본문은 원칙적으로 수집 범위에서 "
             "제외하되, **하랑랑 스페셜(menuId 9) 운영자 정보글 15건은 2026-10-04 사용자 최종 지시로 중요자료로 보존**해 "
             "본문에 실었습니다(작성일이 2024-11-05이므로 과거 시점 자료로 읽고, 회원 댓글 경험도 함께 취합했습니다). "
             "그 외 게시판의 운영자 작성 본문은 부록에 목록만 남겼습니다.")
    L.append("2. **부실차주 임시안부터 확정안(menuId 4)** 글들은 그해 논의되던 임시안~확정안 단계의 기록입니다. "
             "**지금은 시행되지 않을 수 있는 내용**이므로 역사 자료로만 사용하고, 현재 규정으로 인용하지 마세요.")
    L.append("3. 회원 간 대화의 순서(상황 → 행동 → 금융회사·기관 반응 → 결과)를 유지하기 위해 본문과 댓글을 **전문 그대로** "
             "실었습니다. 연락처·계좌 등 숫자열은 전달본에서만 마스킹했습니다.")
    L.append("")
    L.append("## 수집 요약")
    L.append("")
    L.append("| 게시판 | menuId | 조사 | 기존중복 | 가입인사·등업 제외 | 원문 보존 | 운영자 글(반영 제외) | 운영자 글(지시 보존) | 반영 범위 자료 | 지식 단위 |")
    L.append("|---|---|---|---|---|---|---|---|---|---|")
    for b in raw["boards"]:
        L.append("| %s | %d | %d | %d | %d | %d | %d | %d | %d | %d |" % (
            b["boardName"], b["menuId"], b["surveyed"], b["existingDuplicate"], b["greetingExcluded"],
            b["collected"], b["operatorPostsExcluded"], b["operatorPostsKeptByRuling"],
            b["postsInScope"], b["units"]))
    L.append("| **합계** |  | %d | %d | %d | %d | %d | %d | %d | %d |" % (
        sum(b["surveyed"] for b in raw["boards"]), sum(b["existingDuplicate"] for b in raw["boards"]),
        sum(b["greetingExcluded"] for b in raw["boards"]), sum(b["collected"] for b in raw["boards"]),
        sum(b["operatorPostsExcluded"] for b in raw["boards"]),
        sum(b["operatorPostsKeptByRuling"] for b in raw["boards"]),
        sum(b["postsInScope"] for b in raw["boards"]), sum(b["units"] for b in raw["boards"])))
    L.append("")
    L.append("비고: 원본 articleId %d건은 이미 보존된 %d건 집합과 중복 0건입니다. "
             "본문 없이 이미지/첨부만 있는 글 %d건, 스레드가 길어 댓글이 부분 수집된 글 %d건은 각 항목에 표시했습니다."
             % (raw["counts"]["articles"], dedup["article_level"]["baseline_total"],
                sum(1 for r in recs if r["collectionStatus"] == "EMPTY_BODY"),
                sum(1 for r in recs if r["collectionStatus"] == "COMMENTS_PARTIAL")))
    L.append("")
    if db:
        L.append("## 운영 DB 반영 결과 (2026-10-04)")
        L.append("")
        L.append("- 경로: %s" % db["executionChannel"]["how"])
        L.append("- 사용 API: %s" % " · ".join(x.strip() for x in db["executionChannel"]["endpoints"]))
        L.append("- 쓰지 않은 것: %s" % " · ".join(db["executionChannel"]["notUsed"]))
        L.append("")
        L.append("| 게시판 | menuId | 적재 | ACTIVE | REVIEW 보류 | 보류: 이미지/링크 | 보류: 댓글 부분 |")
        L.append("|---|---|---|---|---|---|---|")
        for m in BOARD_ORDER:
            pb = db["perBoard"]["m%d" % m]
            L.append("| %s | %d | %d | %d | %d | %d | %d |" % (
                pb["boardName"], m, pb["created"], pb["active"], pb["reviewHeld"],
                pb["holdMedia"], pb["holdPartial"]))
        L.append("| **합계** |  | %d | %d | %d | %d | %d |" % (
            dbc["newlyCreated"], dbc["approvedActive"], dbc["heldAtReview"],
            db["holdReasons"]["HOLD_MEDIA"], db["holdReasons"]["HOLD_PARTIAL"]))
        L.append("")
        L.append("- 운영 DB %d건 → %d건, 정확일치 기존 %d건, 신규 적재 %d건, DRAFT 잔존 %d건, 생성·승인 오류 %d건."
                 % (dbc["prodRowsBefore"], dbc["prodRowsAfter"], dbc["exactMatchExistingBefore"],
                    dbc["newlyCreated"], dbc["draftRemaining"],
                    dbc["createFailures"] + dbc["approveFailures"]))
        L.append("- REVIEW 보류 자료는 근거가 불완전해 억지로 ACTIVE 로 올리지 않았습니다(지시대로). "
                 "하랑랑 스페셜 운영자 정보글 15건(16 unit)은 작성일 2024-11-05·provenance·historicalCANDIDATE=true 를 "
                 "그대로 보존한 채 REVIEW 단계에 있습니다.")
        L.append("")
        L.append("### 이번 반영에서 생긴 결함과 시정")
        L.append("")
        for note in db["defectsAndDisclosures"]:
            L.append("- %s" % note)
    else:
        L.append("## 운영 DB 반영")
        L.append("")
        L.append("- 아직 미반영. DRY_RUN·WRITE_REVIEW 실행과 REVIEW→ACTIVE 승인은 사용자 전용.")
    L.append("")

    excluded_agent_ids = {r["articleId"] for r in recs
                          if r["classification"]["collectionScope"] == "EXCLUDED_OPERATOR_AGENT_POST"}
    for menu in BOARD_ORDER:
        all_sub = [r for r in recs if r["menuId"] == menu]
        all_sub.sort(key=lambda r: r["writtenAt"] or "", reverse=True)
        sub = [r for r in all_sub if r["articleId"] not in excluded_agent_ids]
        L.append("\n---\n")
        L.append("## %s (menuId %d) — 자료 %d건 (반영 범위 %d + 운영자 글 반영 제외 %d)\n"
                 % (BOARD_TITLE[menu], menu, len(all_sub), len(sub), len(all_sub) - len(sub)))
        if not all_sub:
            det = index["boards_detail"][str(menu)]
            L.append("이 게시판은 이번 tranch에서 **수집 0건**입니다.")
            L.append("")
            L.append("- 조사한 글: %s건" % det["surveyed"])
            L.append("- 운영자(등급 999) 정보 링크 정리글: %s건 — NewStart 수집 범위(회원 정보글만 취합)에서 제외"
                     % det.get("operatorInfoPostsDeclared", 0))
            L.append("- 가입인사·등업·안부: %s건 — 지시 §5에 따라 제외" % det["greetingExcludedDeclared"])
            L.append("- 제외 기준이 애매한 글은 삭제하지 않고 후보로 남깁니다(사용자 확인 대기).")
            continue
        if menu == 4:
            L.append("> ⚠ 아래 자료는 **과거 임시안~확정안 논의 시점의 기록**입니다. 작성일·적용 시점을 함께 읽고, "
                     "현재 시행 규정으로 쓰지 마세요.\n")
        if menu == 9:
            L.append("> ℹ 하랑랑 스페셜 32건 중 **가입인사·등업 17건은 제외**(지시 §5), "
                     "**운영자(지피지기자산) 정보글 15건은 2026-10-04 사용자 최종 지시로 전부 보존**했습니다. "
                     "15건은 2024-11-05 작성 자료이고 본문이 이미지·링크 카드로 구성되어, "
                     "텍스트 근거가 보강되기 전까지 ACTIVE가 아니라 REVIEW 단계에 둡니다.\n")
        for r in all_sub:
            date = (r["writtenAt"] or "")[:10]
            L.append("### " + (mask(r["title"]).strip() or "(제목 없음)"))
            L.append("")
            L.append("- 게시판: %s (menuId %d) · 글번호: %d · 작성일: %s · 작성자: %s(%s) · 조회: %s"
                     % (r["boardName"], menu, r["articleId"], date,
                        mask(str(r["author"].get("nickname") or "")),
                        r["author"].get("memberGrade") or "-", r["viewCount"]))
            L.append("- 원문: %s · 수집일: %s · 댓글 %s개 중 %s개 보존"
                     % (r["sourceUrl"], (r["collectedAt"] or "")[:10],
                        r["commentCountDeclared"], len(r["comments"])))
            flags = []
            if r["collectionStatus"] == "EMPTY_BODY":
                flags.append("본문 이미지/첨부만(텍스트 본문 없음)")
            if r["collectionStatus"] == "COMMENTS_PARTIAL":
                flags.append("댓글 부분 수집")
            if r["classification"]["historical"]:
                flags.append("과거 임시안 후보(historical)")
            if r["articleId"] in excluded_agent_ids:
                flags.append("운영자(agent) 작성글 — 본문 반영 제외, 회원 댓글만 취합")
            elif r.get("scopeIncludedByUserRuling"):
                flags.append("운영자(지피지기자산) 작성 — 2026-10-04 사용자 지시로 중요자료 보존·반영 범위 포함")
            if r.get("contentForm") == "IMAGE_ONLY":
                flags.append("이미지 중심 자료(텍스트 본문 없음) — ACTIVE 보류, REVIEW 유지")
            if r.get("contentForm") == "LINK_PREVIEW":
                flags.append("외부 링크 미리보기 자료(본문은 링크 카드 텍스트)")
            flags.append("공식확인 전(NOT_CHECKED)")
            L.append("- 상태: " + " · ".join(flags))
            if r["classification"]["historical"]:
                L.append("- 주의: %s" % r["boardNote"])
            if menu == 9 and r.get("scopeIncludedByUserRuling"):
                L.append("- 주의: %s" % r["boardNote"])
            L.append("")
            body = mask(r["body"]).strip()
            if r["articleId"] in excluded_agent_ids:
                L.append("**본문**: 운영자(agent) 작성글이라 수집 범위에서 제외 — 본문은 싣지 않습니다. "
                         "원문 URL로 확인할 수 있고, 이 글 아래의 **회원 댓글 경험**만 반영 후보로 취합했습니다.\n")
            elif body:
                L.append("**본문**\n")
                L.append(body)
                L.append("")
            elif r["collectionStatus"] == "EMPTY_BODY":
                names = [(x.get("name") or x.get("url", "")) if isinstance(x, dict) else str(x)
                         for x in (r["attachments"] or [])]
                if r.get("images"):
                    L.append("**본문**: 텍스트 본문 없이 이미지 %d장으로 구성(원본 형태). 이미지 URL을 보존합니다.\n"
                             % len(r["images"]))
                    for i, url in enumerate(r["images"], 1):
                        L.append("- 이미지 %d: %s" % (i, url))
                    for url in (r.get("links") or []):
                        L.append("- 첨부 링크: %s" % url)
                    L.append("")
                else:
                    L.append("**본문**: 텍스트 없음. 첨부%s\n"
                             % ((" " + ", ".join(str(n) for n in names)) if names else ""))
            if r["comments"]:
                L.append("**댓글 (시간순)**\n")
                for c in sorted(r["comments"], key=lambda c: c.get("createdAtMs") or 0):
                    who = "운영자" if c.get("isOperator") else ("글쓴이" if c.get("isAuthor") else "회원")
                    thread = " ↳ " if c.get("parentCommentId") else ""
                    L.append("- %s%s (%s / %s / %s): %s" % (
                        thread, mask(str(c.get("author") or "")), c.get("memberGrade") or "-", who,
                        (c.get("createdAt") or "")[:16].replace("T", " "), mask(c.get("text") or "").replace("\n", " ")))
                L.append("")
            if r["knowledgeUnits"]:
                L.append("**이 글에서 뽑은 경험·정보 단위**\n")
                for u in r["knowledgeUnits"]:
                    L.append("- [%s] %s" % (u["label"], mask(u["evidenceText"] or "").replace("\n", " ")[:400]))
                L.append("")
    L.append("\n---\n")
    L.append("## 부록 — 운영자(agent) 작성 게시글로서 반영 범위에서 제외된 목록 (원문은 리포지토리에 보존)\n")
    L.append("NewStart 수집 범위는 **회원 정보글**입니다. 아래 %d건은 카페매니저(등급 999) 계정이 작성한 글이라 "
             "본문·운영자 단위를 반영 후보에서 제외했습니다(제외 단위 %d건). 각 글 아래의 회원 댓글은 위에 실렸습니다. "
             "**예외**: 하랑랑 스페셜(menuId 9)의 운영자 정보글 15건은 2026-10-04 사용자 최종 지시로 반대로 보존·반영되어 "
             "위 본문에 운영자 안내 단위로 실렸습니다.\n"
             % (len(excluded_agent_ids), units["stats"].get("operatorScopeUnitsDropped", 0)))
    L.append("")
    L.append("| menuId | 글번호 | 제목 | 작성일 | 원문 | 이 글에서 취한 회원 단위 |")
    L.append("|---|---|---|---|---|---|")
    for r in sorted([r for r in recs if r["articleId"] in excluded_agent_ids],
                    key=lambda r: (r["menuId"], r["articleId"])):
        L.append("| %d | %d | %s | %s | %s | %d |" % (
            r["menuId"], r["articleId"], mask(r["title"]).replace("|", "/").replace("\n", " ")[:60],
            (r["writtenAt"] or "")[:10], r["sourceUrl"], len(r["knowledgeUnits"])))
    md = "\n".join(L) + "\n"
    for d in dests:
        (d / md_name).write_text(md, encoding="utf-8")
    print("WROTE %s (%d bytes), %s (%d bytes)" % (raw_name, (pkg / "delivery" / raw_name).stat().st_size,
                                                  md_name, len(md.encode("utf-8"))))
    print("DESTS", [str(d) for d in dests])


if __name__ == "__main__":
    main()
