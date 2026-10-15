# DB_STATUS — PHASE1 부실우려(menuId=6) CAFE_QA 적재 상태 / BLOCKER

- 작성: 2026-09-30 (ACADEMY PC, worktree `feat/newstart-cafe-qa-phase1-20260930` @42f6fc5e)
- 범위: RAW→**STRUCTURE**→**DEDUPE**→**DB-ready payload**→**A to Z CONTENT_READY** 완료.
  **REVIEW INSERT / ADMIN APPROVAL / ACTIVE / RAG**는 아래 게이트로 **미실행(BLOCKER)**.

## 완료 산출물 (git 추적 대상)
- `cafe-qa-phase1-b6/qa_units.json` — 1,000 의미단위, provenance·dedup·relation 보존, 전 unit `officialCheck=NOT_CHECKED`.
- `cafe-qa-phase1-b6/cafe_dedup_report.json` — 1,000 unique `dedup_key`(충돌 0), 기존 ingest 보드와 article 겹침 0, relation 250.
- `cafe-qa-phase1-b6/cafe_qa_db_ready.json` — 기존 스키마(`newstart_knowledge_items`)·REVIEW 파이프라인 계약 그대로 매핑된 페이로드
  (status=REVIEW 강제, sourceType=기존 enum 재사용, human displayLabel, batchId `cafe-qa-b6-2026-09-30`).
- `cafe-qa-phase1-b6/A_TO_Z_CONTENT_READY.md` — STEP 7 매핑 + paste-ready HTML fragment(UI 파일 미수정).

## DB BLOCKER (정확한 사유)
1. **운영 DB 접근 불가(로컬)**: 이 PC에 AWS credential 없음(`aws` 미설치), 로컬 MySQL 3308 리스너 없음.
   → 운영 조회/쓰기는 GitHub Actions(OIDC role) 경로로만 가능 (HOME handoff §FAIL-3과 동일 조건).
2. **정상 ADMIN session 미확보**: in-app Browser는 about:blank, feel2real.co.kr `/api/admin/newstart/*` 호출에 필요한
   ACCESS_TOKEN 쿠키가 없음. ADMIN login id/password는 **GitHub Secrets**(서버측 전용)로 에이전트가 평문 취득/우회 인증하지 않음(§9).
3. **cafe-qa ingest 엔드포인트 미데플로이**: 현행 EC2 jar의 `/api/admin/newstart/wiki`에는 curated-review / official-review /
   cases·repair만 존재. 지시(§5)대로 새 ingest framework를 이번 세션에 신설·배포하지 않았으므로 cafe_qa 페이로드를 받을
   쓰기 경로가 프로덕션에 아직 없음.
4. **ACTIVE 승인은 별도 human/admin 워크플로**: seed 도구는 `/review/{id}/approve`를 의도적으로 호출하지 않음.
   REVIEW→ACTIVE는 관리자 승인 전용. 직접 SQL ACTIVE / seed 직행 금지(§6/§9).

## 정상 경로로 이어서 실행할 단계 (ADMIN session or deploy 확보 시)
A. **Live delta 1회(선택, 읽기전용)**: `gh workflow run newstart-prod-census-readonly.yml -R woori73/feel2real-buildit`
   → 기존 empirical_cases article_id·knowledge status/source_type 집계로 prod 기존분과 겹침 최종 확인(쓰기 없음).
B. **REVIEW 적재(쓰기)** — 둘 중 하나:
   - (B1) 정상 ADMIN session으로 기존 검증된 REVIEW 적재 경로 사용, 또는
   - (B2) 최소 CAFE_QA ingest(`cafe_qa_db_ready.json` 계약으로 `NewstartKnowledgeItem` status=REVIEW save,
     `dedup_key` UNIQUE·findByTitle 멱등, 기존 entity/repo 재사용·신 migration 없음) 를 service+endpoint로 추가 →
     `newstart-seed.yml`에 CAFE_QA phase(dry_run 기본 true) → merge/CI/deploy → `dry_run=true` 보고 → `dry_run=false`(REVIEW만).
     단, 이는 §5 "새 framework 금지"와 충돌하므로 사용자 승인 하에만.
C. **ADMIN 승인 → ACTIVE**: `/review/{id}/approve`(관리자) 또는 운영 ADMIN UI. 절대 직접 SQL.
D. **RAG 확인**: ACTIVE 후 지피지기 실제 질문으로
   사례/경험→차이→공식자료→불확실성 순 답변 확인(`NewstartKnowledgeRetrievalService` status=ACTIVE+LIKE).

## 불변식 준수 확인
- 기존 자료 삭제/overwrite 0, 카페 답변→공식 승격 0(전 unit NOT_CHECKED), 540/3768 IMAGE_ONLY(추측·blocker 아님),
  PHASE 2(menuId=7) 미착수.
