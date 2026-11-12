#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
4개 게시판(menuId 3/4/5/9) 수집분의 중복·분류 검수 리포트를 만든다.

기존 b6 파이프라인의 계약을 재사용하며 신규框架를 발명하지 않는다:
- article 단위: repository에 이미 보존된 articleId集合(baseline_ids.json)과 교차검증.
  baseline의 모든 id는 저장소 문서/자료에서 실재 확인된다(출처 경로 병기).
- unit 단위: load_cafe_4boards_prod.py의 exact-match 스킵과 동일한 ck(dedup_key)를
  배치 페이로드에서 역추출해 (가) 패키지 내 중복 (나) cafe-qa-phase1-b6 992건과의 충돌을 판정.
- 운영 DB 실시간 exact-match은 ADMIN 인증이 필요한 read-only DRY_RUN으로만 수행된다.
  이 스크립트는 DB를 읽지도 쓰지도 않으며, 승인(approve)과 무관하다.

입력 : INDEX.json, qa_units.json, load_batches/*.json, enumeration/baseline_ids.json,
       ../cafe-qa-phase1-b6/load_batches/*.json
출력 : cafe_4boards_dedup_report.json
"""
from __future__ import annotations

import argparse
import collections
import glob
import hashlib
import json
import os
import pathlib
import re
import unicodedata

B6_ROOT = "docs/newstart/research/cafe-qa-phase1-b6"


def norm(s: str) -> str:
    s = (s or "").replace("\u200b", "").strip()
    s = re.sub(r"\s+", " ", s)
    return unicodedata.normalize("NFKC", s)


def ck_of(tags: str) -> str:
    for part in (tags or "").split(","):
        if part.startswith("ck="):
            return part[3:]
    return ""


def read_json(p):
    return json.loads(pathlib.Path(p).read_text(encoding="utf-8"))


def payloads(root):
    manifest = read_json(os.path.join(root, "load_batches", "manifest.json"))
    rows = []
    for b in manifest["batches"]:
        rows.extend(read_json(os.path.join(root, "load_batches", b["file"])))
    return manifest, rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", help="package dir, e.g. docs/newstart/research/cafe-4boards-20261004")
    ap.add_argument("--repo", default=".", help="repo root (for baseline provenance scan)")
    ap.add_argument("--b6", default=B6_ROOT, help="existing cafe-qa package for cross collision")
    args = ap.parse_args()

    root = pathlib.Path(args.root)
    index = read_json(root / "INDEX.json")
    units = read_json(root / "qa_units.json")
    manifest, rows = payloads(root)

    baseline = set(read_json(root / "enumeration" / "baseline_ids.json"))
    collected = sorted(index.get("articles", []), key=lambda a: a["articleId"]) \
        if isinstance(index.get("articles"), list) else []
    art_ids = sorted({int(a["articleId"]) for a in collected}) if collected else \
        sorted(int(re.search(r"article_(\d+)\.json", f).group(1))
               for f in glob.glob(str(root / "articles" / "article_*.json")))

    # (1) baseline 출처 실증: 저장소 텍스트에서 각 id의 등장 여부 확인
    repo_hits = collections.defaultdict(list)
    for f in glob.glob(args.repo + "/docs/newstart/**/*.*", recursive=True) + \
            glob.glob(args.repo + "/data/newstart-cafe/**/*.*", recursive=True):
        if "cafe-4boards" in f or not f.endswith((".md", ".json", ".jsonl", ".csv", ".txt", ".yml", ".yaml")):
            continue
        try:
            text = open(f, encoding="utf-8").read()
        except Exception:
            continue
        found = set(int(x) for x in re.findall(r"(?<!\d)(\d{3,5})(?!\d)", text)) & baseline
        for i in found:
            if len(repo_hits[i]) < 3:
                repo_hits[i].append(os.path.relpath(f, args.repo).replace("\\", "/"))
    unattested = sorted(baseline - set(repo_hits))

    overlap = sorted(set(art_ids) & baseline)

    # (2) unit dedup_key
    keys = [ck_of(r.get("tags", "")) for r in rows]
    dup_within = [k for k, c in collections.Counter(keys).items() if c > 1]
    _, b6_rows = payloads(args.b6)
    b6_keys = {ck_of(r.get("tags", "")) for r in b6_rows}
    collide_b6 = sorted(set(keys) & b6_keys)

    # (3) 정규화 제목 충돌 (패키지 내)
    tnorm = collections.Counter(norm(r.get("title", "")) for r in rows)
    dup_title = {t: c for t, c in tnorm.items() if c > 1}

    # (4) 본문 지문 충돌 (유닛 evidence 기준)
    fp = collections.Counter(hashlib.sha256(norm(u.get("evidence", "")).encode("utf-8")).hexdigest()
                             for u in units["units"])
    dup_fingerprint = sum(1 for _, c in fp.items() if c > 1)

    # (5) 게시판별 집계
    per_board = {}
    for menu, det in index["boards_detail"].items():
        per_board["menuId" + menu] = {
            "boardName": det["menuName"],
            "surveyed": det["surveyed"],
            "enumerationTotal": det.get("enumerationTotal"),
            "existingDuplicateDeclared": det["existingDuplicateDeclared"],
            "greetingExcludedDeclared": det["greetingExcludedDeclared"],
            "operatorInfoPostsDeclared": det.get("operatorInfoPostsDeclared", 0),
            "newCandidatesDeclared": det["newCandidatesDeclared"],
            "collectedNewArticles": det["collected"],
            "operatorAuthoredArticles": det.get("operatorAuthoredArticles", 0),
            "informational": det["informational"],
            "emptyBodyImageOnly": det["emptyBody"],
            "commentsPartial": det["commentsPartial"],
            "historicalCandidates": det["historicalCandidates"],
            "overlapWithBaseline": det["alreadyInBaseline"],
            "payloadRows": 0,
            "duplicateIds": det["duplicateIds"],
        }
    for r in rows:
        m = re.search(r"menuId(\d+)", r.get("tags", ""))
        if m:
            k = "menuId" + m.group(1)
            per_board[k]["payloadRows"] += 1

    report = {
        "package": index["package"],
        "generatedBy": "build_cafe_4boards_dedup_report.py",
        "scope": {
            "boards": [3, 4, 5, 9],
            "cafeKey": index["cafe"]["cafeKey"],
            "clubId": index["cafe"]["clubid"],
            "note": "지시 §1의 4개 게시판만 대상. 타 게시판·운영자 정리글은 범위 외.",
        },
        "idempotency_contract": {
            "knowledge_items": "dedup_key UNIQUE + findByTitle / loader의 14필드 exact-match skip",
            "empirical_cases": "article_id UNIQUE",
            "write_policy": "insert-only. update/delete/approve 없음. DRAFT->REVIEW만.",
            "prod_db_check": "ADMIN 시크릿으로 실행하는 read-only DRY_RUN에서만 수행 (본 리포트는 DB 미접근)",
        },
        "article_level": {
            "baseline_source": "enumeration/baseline_ids.json (기존 보존 articleId 전수)",
            "baseline_total": len(baseline),
            "baseline_attested_in_repo": len(baseline) - len(unattested),
            "baseline_unattested_ids": unattested[:50],
            "collected_total": len(art_ids),
            "overlap_with_baseline": len(overlap),
            "overlap_ids": overlap,
            "per_source_files": {k: v for k, v in sorted(
                collections.Counter(p for hs in repo_hits.values() for p in hs).items(),
                key=lambda kv: -kv[1])[:12]},
        },
        "unit_level": {
            "payload_rows": len(rows),
            "unique_dedup_keys": len(set(keys)),
            "duplicate_keys_within_package": dup_within,
            "existing_b6_keys": len(b6_keys),
            "collisions_with_b6": len(collide_b6),
            "collision_examples": collide_b6[:10],
            "normalized_title_collision_groups": len(dup_title),
            "normalized_title_collisions": {k[:80]: v for k, v in sorted(
                dup_title.items(), key=lambda kv: -kv[1])[:20]},
            "identical_evidence_fingerprints": dup_fingerprint,
        },
        "classification": {
            "rule": "build_qa_units.process_article 재사용(규칙 복제 없음). noise unitdropped는 잡담/경조사만.",
            "articlesTotal": units["stats"]["articlesTotal"],
            "articlesWithUnits": units["stats"]["articlesWithUnits"],
            "unitsEmitted": units["stats"]["unitsEmitted"],
            "noiseUnitsDropped": units["stats"]["noiseUnitsDropped"],
            "operatorScopeUnitsDropped": units["stats"].get("operatorScopeUnitsDropped", 0),
            "operatorAuthoredArticles": units["stats"].get("operatorAuthoredArticles", 0),
            "byType": units["stats"]["byType"],
            "byBoard": units["stats"]["byBoard"],
            "historical_handling": {
                "menuId4": "과거 임시안~확정안 게시판 전량을 historicalCandidate=true로 표기. "
                            "current 정책으로 오인 금지. 실제 적용시점은 원문 작성일만 보존(추측 날짜 미부여).",
                "schema": "신규 컬럼/값 발명 없음 — existing SourceType + tags.historicalCANDIDATE + RAW historicalCandidate",
            },
            "operator_scope": {
                "rule": "작성자가 운영자(agent) 계정(등급 999 / 카페매니저)인 게시글은 NewStart 수집 범위(회원 정보글만 취합)에서 제외."
                        "해당 글의 본문 단위와 운영자 단위만 drop하고, 그 글 아래의 회원 댓글 단위는 취합한다.",
                "rawPreserved": True,
                "reflectedInPayloads": False,
                "exception": "2026-10-04 사용자 최종 지시: menuId 9(하랑랑 스페셜) 운영자 정보글 15건은 중요자료로 보존 → "
                             "OPERATOR_KNOWLEDGE 단위로 payload에 반영(sourceType=OPERATOR_KNOWLEDGE). "
                             "타 게시판의 운영자 본문 단위는 종전 제외 범위 유지.",
                "operatorKeptArticles": index["boards_detail"]["9"]["collected"],
                "operatorKeptUnits": units["stats"].get("operatorKeptUnits", 0),
            },
            "menu9_handling": {
                "operatorInfoPostsDeclared": index["boards_detail"]["9"]["operatorInfoPostsDeclared"],
                "greetingExcludedDeclared": index["boards_detail"]["9"]["greetingExcludedDeclared"],
                "collected": index["boards_detail"]["9"]["collected"],
                "units": units["stats"]["byBoard"].get("m9", 0),
                "reason": index["boards_detail"]["9"]["collectionExcluded"],
                "conflict_flag": "RESOLVED_BY_USER 2026-10-04 — 지시 §5(하랑랑 경험글 포함)와 운영 수집범위(운영자 정리글 제외) "
                                 "충돌은 사용자로써 종료. 가입인사·등업 17건 제외 유지 / 운영자 정보글 15건은 전량 보존.",
                "evidence_weakness": "15건 중 13건은 본문이 이미지만으로, 2건은 외부 링크 미리보기 카드로만 구성됨 "
                                     "(contentForm=IMAGE_ONLY/LINK_PREVIEW). 근거 텍스트가 약해 REVIEW에서 ACTIVE 보류 대상.",
                "re_collection": "1차 tranch에서 15건의 전문이 보존되지 않아(목록 메타만) 본문·이미지·댓글 증거 확보용으로 "
                                 "해당 15건만 재접속 수집함. 4개 게시판 재수집은 아님.",
            },
        },
        "per_board": per_board,
        "totals": {
            "surveyed": sum(v["surveyed"] for v in per_board.values()),
            "excluded_greeting": sum(v["greetingExcludedDeclared"] for v in per_board.values()),
            "excluded_operator_posts": sum(v["operatorInfoPostsDeclared"] for v in per_board.values()),
            "existing_duplicates": sum(v["existingDuplicateDeclared"] for v in per_board.values()),
            "new_valid_articles": sum(v["collectedNewArticles"] for v in per_board.values()),
            "operatorAuthoredArticles": sum(v["operatorAuthoredArticles"] for v in per_board.values()),
            "operatorScopeUnitsDropped": units["stats"].get("operatorScopeUnitsDropped", 0),
            "payload_rows_planned": len(rows),
        },
        "not_done": [
            "운영 DB read-only exact-match DRY_RUN(ADMIN 시크릿 필요 — 사용자 실행)",
            "REVIEW->ACTIVE 승인(사람 전용)",
            "ChatGPT Library 업로드(Qoder는 전달파일 2종만 제작)",
        ],
    }

    out = root / "cafe_4boards_dedup_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("WROTE", out)
    print(json.dumps(report["totals"], ensure_ascii=False))
    print("unit:", json.dumps({k: report["unit_level"][k] for k in
                               ("payload_rows", "unique_dedup_keys", "duplicate_keys_within_package",
                                "collisions_with_b6", "identical_evidence_fingerprints")}, ensure_ascii=False))
    print("article:", json.dumps({k: report["article_level"][k] for k in
                                  ("baseline_total", "baseline_attested_in_repo", "collected_total",
                                   "overlap_with_baseline")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
