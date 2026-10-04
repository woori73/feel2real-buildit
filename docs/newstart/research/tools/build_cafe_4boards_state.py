#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""패키지 산출물(INDEX/qa_units/dedup_report)에서 상태·체크포인트 파일을 재생성한다.

collection_state.json / checkpoint.json 은 gamian(data/newstart-cafe/gamian/) 규약을 따른다.
수집은 tranch(고가치 우선) 단위로 진행되므로 status는 COMPLETED가 아니라
TRANCH_COMPLETED이며, 미수집 후보 수는 pendingCount로 명시한다(완료 과장 금지).
"""
from __future__ import annotations

import datetime as dt
import json
import pathlib
import sys

PKG = pathlib.Path(sys.argv[1])
index = json.loads((PKG / "INDEX.json").read_text(encoding="utf-8"))
units = json.loads((PKG / "qa_units.json").read_text(encoding="utf-8"))
dedup = json.loads((PKG / "cafe_4boards_dedup_report.json").read_text(encoding="utf-8"))
manifest = json.loads((PKG / "load_batches" / "manifest.json").read_text(encoding="utf-8"))

now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
boards = []
surveyed = collected = pending = dup = greeting = operator_posts = 0
for menu, det in sorted(index["boards_detail"].items(), key=lambda kv: int(kv[0])):
    b = {
        "menuId": menu,
        "boardName": det["menuName"],
        "surveyed": det["surveyed"],
        "existingDuplicate": det["existingDuplicateDeclared"],
        "greetingExcluded": det["greetingExcludedDeclared"],
        "operatorInfoExcluded": det.get("operatorInfoPostsDeclared", 0),
        "newCandidates": det["newCandidatesDeclared"],
        "trancheRanked": det.get("rankedCandidates", 0),
        "collected": det["collected"],
        "operatorAuthoredArticles": det.get("operatorAuthoredArticles", 0),
        "informational": det["informational"],
        "emptyBody": det["emptyBody"],
        "commentsPartial": det["commentsPartial"],
        "historicalCandidates": det["historicalCandidates"],
        "units": units["stats"]["byBoard"].get("m%s" % menu, 0),
        "pendingUnrankedCandidates": max(det["newCandidatesDeclared"] - det.get("rankedCandidates", 0), 0),
    }
    boards.append(b)
    surveyed += b["surveyed"]
    collected += b["collected"]
    dup += b["existingDuplicate"]
    greeting += b["greetingExcluded"]
    operator_posts += b["operatorInfoExcluded"]
    pending += b["pendingUnrankedCandidates"]

state = {
    "task": "NewStart cafe 4-board collection (menuId 3/4/5/9)",
    "cafe": {"name": index["cafe"]["name"], "cafeKey": index["cafe"]["cafeKey"], "clubId": index["cafe"]["clubid"]},
    "package": index["package"],
    "packagePath": str(PKG).replace("\\", "/"),
    "status": "TRANCH_COMPLETED_PENDING_USER_APPROVAL",
    "tranche": "고가치 우선 110건 + 하랑랑 스페셜 운영자 정보글 15건(2026-10-04 사용자 지시 보존) = 125건. "
               "전체 신규 후보 중 미수집분은 pendingCounts 참조",
    "startedAt": "2026-10-04T00:00:00+00:00",
    "updatedAt": now,
    "totals": {
        "surveyed": surveyed,
        "existingDuplicate": dup,
        "greetingExcluded": greeting,
        "operatorInfoExcluded": operator_posts,
        "collectedNewArticles": collected,
        "operatorAuthoredArticles": sum(b["operatorAuthoredArticles"] for b in boards),
        "operatorScopeUnitsDropped": units["stats"].get("operatorScopeUnitsDropped", 0),
        "baselineArticleIds": dedup["article_level"]["baseline_total"],
        "overlapWithBaseline": dedup["article_level"]["overlap_with_baseline"],
        "units": units["stats"]["unitsEmitted"],
        "noiseUnitsDropped": units["stats"]["noiseUnitsDropped"],
        "payloadRows": manifest["totalToLoad"],
        "batches": manifest["batchCount"],
        "pendingUnrankedCandidates": pending,
    },
    "boards": boards,
    "dbReflection": {
        "writtenToProd": False,
        "approved": False,
        "path": "insert-only DRAFT->REVIEW via existing admin wiki API, workflow newstart-cafe-4boards-load.yml",
        "blocker": "로컬에 ADMIN 세션/AWS 자격 없음. DRY_RUN·WRITE_REVIEW 실행과 REVIEW->ACTIVE 승인은 사용자 전용.",
    },
    "qualityFlags": {
        "emptyBodyImageOnly": sum(b["emptyBody"] for b in boards),
        "commentsPartial": sum(b["commentsPartial"] for b in boards),
        "historicalCandidatesMenuId4": units["stats"]["byBoard"].get("m4", 0),
        "menu9Collected": units["stats"]["byBoard"].get("m9", 0),
        "menu9OperatorArticlesKept": index["boards_detail"]["9"].get("collected", 0),
        "menu9OperatorUnitsKept": units["stats"].get("operatorKeptUnits", 0),
        "menu9GreetingExcluded": index["boards_detail"]["9"]["greetingExcludedDeclared"],
    },
    "errors": [],
}

checkpoint = {
    "task": state["task"],
    "package": index["package"],
    "phase": "RAW_PRESERVED_UNITS_BUILT_PR_PENDING",
    "progress": {
        "enumerated": surveyed,
        "collected": collected,
        "units": units["stats"]["unitsEmitted"],
        "payloadRows": manifest["totalToLoad"],
        "pendingCandidates": pending,
    },
    "nextActions": [
        "사용자: PR 검수 후 newstart-cafe-4boards-load.yml DRY_RUN 실행(운영 DB read-only 중복 실측)",
        "사용자: 필요 시 WRITE_REVIEW 실행(insert-only, 승인 없음)",
        "사람: ADMIN 화면에서 REVIEW->ACTIVE 승인",
        "다음 tranch: " + " / ".join("menuId%s %d" % (b["menuId"], b["pendingUnrankedCandidates"])
                                    for b in boards if b["pendingUnrankedCandidates"]),
    ],
    "checkpointAt": now,
    "lastCommitSha": (sys.argv[2] if len(sys.argv) > 2 else None),
    "lastPushAt": now if len(sys.argv) > 2 else None,
}

(PKG / "collection_state.json").write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(PKG / "checkpoint.json").write_text(json.dumps(checkpoint, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("WROTE collection_state.json checkpoint.json")
print(json.dumps(state["totals"], ensure_ascii=False))
