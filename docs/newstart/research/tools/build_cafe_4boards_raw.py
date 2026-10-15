#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""4개 게시판(부실차주 신청 / 임시안→확정안 / 재취업·재창업 / 하랑랑 스페셜) RAW 보존.

입력:
  --dump       브라우저(m.cafe logged-in SPA store)에서 내려받은 기사 JSON 배열 덤프
  --inventory  게시판별 전수 열거 인벤토리 디렉터리 (enumeration/menu{3,4,5,9}.json)
  --baseline   기존 보존 articleId 집합 (404건) — 신규/중복 판정용
  --out        출력 패키지 디렉터리 (docs/newstart/research/cafe-4boards-20261004)

출력:
  articles/article_{id}.json  : b6(cafe-qa-phase1-b6) 호환 canonical RAW
  INDEX.json                  : 게시판별 조사/수집/중복/제외 수치
  enumeration/                : 입력 인벤토리 사본(감사 추적용)

원칙: 원문 보존이 우선이며 본문·댓글을 임의로 생략하지 않는다. 과거 임시안 게시판(menuId=4)은
historical 후보로 표기하되 current/확정 판정은 하지 않는다(사람 확인 필요).
"""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import os
import pathlib
import re
import sys

CAFE_CLUB = 31312978
CAFE_KEY = "newhopero"
OPERATOR_GRADE = "카페매니저"
BOARDS = {
    3: "부실차주 신청",
    4: "부실차주 임시안부터 확정안",
    5: "재취업 재창업 감면수익+정부 지원",
    9: "하랑랑 스페셜",
}
EXTRACTION_METHOD = "Qoder browser (logged-in SPA route /ca-fe/web/cafes/31312978/articles)"


def clean(t: str) -> str:
    if not t:
        return ""
    t = t.replace("​", "")
    t = re.sub(r"[ \t]+\n", "\n", t)
    return t.strip()


def parse_written(v) -> tuple[str, int]:
    """'26.03.10. 12:18' -> (ISO KST, epoch ms)."""
    s = str(v or "").strip()
    m = re.match(r"^(\d{2})\.(\d{2})\.(\d{2})\.\s*(\d{1,2}):(\d{2})", s)
    if not m:
        return (None, None)
    yy, mo, dd, hh, mi = (int(x) for x in m.groups())
    d = dt.datetime(2000 + yy, mo, dd, hh, mi, tzinfo=dt.timezone(dt.timedelta(hours=9)))
    return (d.isoformat(timespec="seconds"), int(d.timestamp() * 1000))


def nick(v):
    """브라우저 스토어는 nickname를 문자열 또는 {nick, memberKey, ...} 객체로 준다.
    memberKey/baMemberKey(회원 해시)는 보존하지 않는다(PII)."""
    if isinstance(v, dict):
        return v.get("nick") or v.get("nickname")
    return v


def grade(v):
    """memberGrade는 {value, name, iconUrl,...} 객체 또는 문자열로 온다."""
    if isinstance(v, dict):
        return v.get("name")
    return v


def level(v):
    if isinstance(v, dict):
        return v.get("value")
    return None


def map_comment(c: dict) -> dict:
    ms = c.get("writtenAtMs") or (c.get("writtenAt") if isinstance(c.get("writtenAt"), (int, float)) else None)
    iso = None
    if ms:
        iso = dt.datetime.fromtimestamp(ms / 1000, dt.timezone(dt.timedelta(hours=9))).isoformat(timespec="seconds")
    elif c.get("writtenAt"):
        iso, ms2 = parse_written(c.get("writtenAt"))
        ms = ms2
    grade_name = (c.get("memberLevelName") or grade(c.get("memberGrade")) or "").strip()
    return {
        "commentId": c.get("id"),
        "parentCommentId": None if c.get("refId") == c.get("id") else c.get("refId"),
        "author": nick(c.get("nickname")),
        "memberGrade": grade_name,
        "createdAt": iso,
        "createdAtMs": ms,
        "text": clean(c.get("content") or ""),
        "isAuthor": bool(c.get("isArticleWriter")),
        "isOperator": grade_name == OPERATOR_GRADE,
        "isDeleted": bool(c.get("isDeleted")),
        "bestComment": bool(c.get("bestComment")),
        "sourceType": "CAFE_COMMENT",
    }


def board_of(aid: int, inventory: dict) -> tuple[int, str, str]:
    for menu, rows in inventory.items():
        if aid in rows:
            return menu, BOARDS[menu], rows[aid]
    return None, None, None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", required=True, nargs="+")
    ap.add_argument("--inventory", required=True)
    ap.add_argument("--baseline", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    out = pathlib.Path(args.out)
    (out / "articles").mkdir(parents=True, exist_ok=True)

    dump = []
    for path in args.dump:
        dump.extend(json.loads(pathlib.Path(path).read_text(encoding="utf-8")))
    seen, dedumped = set(), []
    for rec in dump:
        aid = int(rec["articleId"])
        if aid in seen:
            continue
        seen.add(aid)
        dedumped.append(rec)
    dump = dedumped
    baseline = {int(x) for x in json.loads(pathlib.Path(args.baseline).read_text(encoding="utf-8"))}

    inventory: dict[int, dict[int, str]] = {}
    counts: dict[str, dict] = {}
    for path in sorted(glob.glob(os.path.join(args.inventory, "menu*.json"))):
        inv = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        menu = int(inv["menuId"])
        rows = {}
        for r in (inv.get("topByScore") or []):
            rows[int(r[0])] = inv["menuName"]
        for r in (inv.get("items") or []):
            # 하랑랑 스페셜 인벤토리는 topByScore 없이 items-only 이므로 id 색인을 여기서 만든다.
            rows.setdefault(int(r[0]), inv["menuName"])
        inventory[menu] = rows
        c = inv.get("counts") or {}
        items = inv.get("items") or []
        if not c and items:
            # 하랑랑 스페셜 인벤토리는 columns=[articleId,writeDate,commentCount,readCount,memberLevel,subject]
            op = sum(1 for r in items if r[4] == 999)
            c = {"surveyed": len(items), "existingDuplicate": 0,
                 "greetingExcluded": len(items) - op, "newCandidates": op}
            counts[str(menu)] = {
                "menuName": inv["menuName"],
                "enumerationTotal": inv.get("total") or len(items),
                "surveyed": c["surveyed"],
                "existingDuplicateDeclared": 0,
                "greetingExcludedDeclared": c["greetingExcluded"],
                "operatorInfoPostsDeclared": op,
                "newCandidatesDeclared": 0,
                "rankedCandidates": 0,
                "duplicateIds": [],
                "scopeNote": inv.get("note"),
                "collectionExcluded": "가입인사·등업류는 지시 §5에 따라 제외. "
                                      "운영자(등급 999) 정보글 15건은 2026-10-04 사용자 최종 지시로 보존(중요자료).",
            }
            continue
        counts[str(menu)] = {
            "menuName": inv["menuName"],
            "enumerationTotal": inv.get("total") or len(inv.get("allArticleIds") or []),
            "surveyed": (inv.get("counts") or {}).get("surveyed"),
            "existingDuplicateDeclared": (inv.get("counts") or {}).get("existingDuplicate"),
            "greetingExcludedDeclared": (inv.get("counts") or {}).get("greetingExcluded"),
            "newCandidatesDeclared": (inv.get("counts") or {}).get("newCandidates"),
            "rankedCandidates": len(inv.get("topByScore") or []),
            "duplicateIds": inv.get("duplicateIds") or [],
        }

    collected = []
    for rec in dump:
        aid = int(rec["articleId"])
        menu, board_name, _ = board_of(aid, inventory)
        created_iso, created_ms = parse_written(rec.get("writtenAt"))
        comments = [map_comment(c) for c in (rec.get("comments") or [])]
        op_comments = sum(1 for c in comments if c["isOperator"])
        body = clean(rec.get("bodyNormalized") or "")
        warnings = list(rec.get("parseWarnings") or [])
        if menu is None:
            warnings.append("BOARD_NOT_IN_INVENTORY")
        if not body:
            warnings.append("EMPTY_BODY")
        declared = int(rec.get("commentCount") or 0)
        if declared != len(comments):
            warnings.append("COMMENTS_PARTIAL")
        author_grade = grade((rec.get("author") or {}).get("memberGrade")) or ""
        author_level = level((rec.get("author") or {}).get("memberGrade"))
        # 수집 범위: 운영자(agent) 계정 게시글은 제외, 댓글 내 운영자 답변은 보존 (회원 정보글만 취합)
        operator_authored = author_level == 999 or "매니저" in author_grade
        # 2026-10-04 사용자 최종 지시: 하랑랑 스페셜(menuId 9) 운영자 정보글 15건은 중요자료로 보존.
        # 다른 게시판의 운영자(agent) 계정 본문 단위는 종전 수집 범위(회원 정보글만 취합)를 그대로 따른다.
        operator_kept = operator_authored and bool(rec.get("operatorRuling"))
        art = {
            "articleId": aid,
            "boardId": menu,
            "boardName": board_name,
            "cafeClubId": CAFE_CLUB,
            "cafeKey": CAFE_KEY,
            "title": clean(rec.get("title") or ""),
            "author": {
                "nickname": nick((rec.get("author") or {}).get("nickname")),
                "memberGrade": grade((rec.get("author") or {}).get("memberGrade")),
                "memberLevel": level((rec.get("author") or {}).get("memberGrade")),
            },
            "createdAt": created_iso,
            "createdAtMs": created_ms,
            "writtenAtRaw": rec.get("writtenAt"),
            "viewCount": rec.get("viewCount"),
            "commentCount": declared,
            "commentsCaptured": len(comments),
            "sourceUrl": rec.get("sourceUrl"),
            "sourceType": "CAFE_CASE",
            "topicTags": rec.get("topicTags") or [],
            "bodyRaw": [clean(x) for x in (rec.get("bodyRaw") or []) if clean(x)],
            "bodyNormalized": body,
            "bodyHtml": None,
            "attachments": rec.get("attachments") or [],
            "comments": comments,
            "operatorCommentCount": op_comments,
            "imageRequired": bool(rec.get("imageRequired")),
            "collectedAt": rec.get("collectedAt"),
            "extractionMethod": EXTRACTION_METHOD,
            "collectionStatus": "COMPLETE" if body and declared == len(comments) else (
                "EMPTY_BODY" if not body else "COMMENTS_PARTIAL"),
            "parseWarnings": warnings,
            "historicalCandidate": menu == 4,
            "operatorAuthored": operator_authored,
            "scopeExcluded": None if operator_kept else (
                "OPERATOR_AGENT_POST" if operator_authored else None),
            "boardNote": ("과거 임시안~확정안 게시판: 역사 자료. 현재 시행 규정으로 사용하지 말 것"
                          if menu == 4 else (
                          "하랑랑 스페셜 운영자 정보글: 2026-10-04 사용자 지시로 보존. "
                          "2024-11-05 작성 자료이므로 current 규정으로 오인 금지."
                          if menu == 9 and operator_kept else None)),
        }
        if operator_kept:
            art["scopeIncludedByUserRuling"] = rec.get("operatorRuling")
            art["authorType"] = "OPERATOR"
            art["historicalNote"] = ("작성일 2024-11-05 자료 — 과거 시점 안내. 작성일·출처URL 보존.")
        for key in ("images", "links", "contentForm", "bodyHtmlLength"):
            if rec.get(key):
                art[key] = rec[key]
        (out / "articles" / f"article_{aid}.json").write_text(
            json.dumps(art, ensure_ascii=False, indent=1), encoding="utf-8")
        collected.append(art)

    by_board: dict[str, dict] = {}
    for menu, meta in counts.items():
        arts = [a for a in collected if a["boardId"] == int(menu)]
        by_board[menu] = {
            **meta,
            "collected": len(arts),
            "informational": sum(1 for a in arts if len(a["bodyNormalized"]) >= 80
                                 or a["operatorCommentCount"] >= 1 or a.get("images")),
            "emptyBody": sum(1 for a in arts if a["collectionStatus"] == "EMPTY_BODY"),
            "commentsPartial": sum(1 for a in arts if a["collectionStatus"] == "COMMENTS_PARTIAL"),
            "historicalCandidates": sum(1 for a in arts if a["historicalCandidate"]),
            "operatorAuthoredArticles": sum(1 for a in arts if a["operatorAuthored"]),
            "newVsBaseline": sum(1 for a in arts if a["articleId"] not in baseline),
            "alreadyInBaseline": sum(1 for a in arts if a["articleId"] in baseline),
        }

    index = {
        "package": "cafe-4boards-20261004",
        "cafe": {"clubid": CAFE_CLUB, "cafeKey": CAFE_KEY, "name": "새출발기금 부실차주 라이브러리"},
        "boards": {str(k): BOARDS[k] for k in BOARDS},
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "extractionMethod": EXTRACTION_METHOD,
        "totalCollected": len(collected),
        "baselineIds": len(baseline),
        "userRuling20261004": "하랑랑 스페셜(menuId 9): 가입인사·등업 17건 제외 유지, "
                              "운영자(지피지기자산) 정보글 15건은 중요자료로 보존. "
                              "타 게시판의 운영자 본문 단위는 종전 제외 범위 유지.",
        "newVsBaseline": sum(1 for a in collected if a["articleId"] not in baseline),
        "boards_detail": by_board,
        "articles": [
            {"articleId": a["articleId"], "boardId": a["boardId"], "title": a["title"],
             "createdAt": a["createdAt"], "viewCount": a["viewCount"],
             "commentCount": a["commentCount"], "commentsCaptured": a["commentsCaptured"],
             "collectionStatus": a["collectionStatus"],
             "historicalCandidate": a["historicalCandidate"],
             "operatorAuthored": a["operatorAuthored"],
             "scopeExcluded": a["scopeExcluded"],
             "contentForm": a.get("contentForm")}
            for a in sorted(collected, key=lambda x: (x["boardId"] or 0, x["articleId"]))
        ],
    }
    (out / "INDEX.json").write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"COLLECTED={len(collected)} BOARDS=" +
          " ".join(f"m{k}:{v['collected']}" for k, v in sorted(by_board.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
