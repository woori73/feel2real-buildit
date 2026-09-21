# NewStart Wiki REVIEW 적재·검증 — ACADEMY 작업 결과 (2026-09-21)

대상 branch: `feat/newstart-wiki-review-ingest-2026-09-21-academy`
직전 handoff: `NEWSTART_WIKI_REVIEW_INGEST_HANDOFF_2026-09-21_HOME_TO_ACADEMY.md`

## 1. §10.1 진단 정정 — 100개 컴파일 에러의 실제 원인

HOME 쪽 추측(Gradle 환경 문제 / main 선행 파손)은 **모두 기각**되었다.

- pristine main에서 `compileJava` → BUILD SUCCESSFUL, 에러 0
- 본 branch의 main 대비 diff는 추가 전용

실제 원인은 소스 구조였다. `NewstartKnowledgeDtos.java`(public top-level 5개)와
`NewstartEmpiricalCaseDtos.java`(public 3개)가 "파일당 public top-level 클래스 1개"
규칙을 어겨 javac의 annotation-processing round가 중단되었고, 그 결과 Lombok이
아무 멤버도 생성하지 못해 **무관한 파일들까지 cannot find symbol**를 냈다(카스케이드).
DTO를 8개 파일로 분리하자 100 → 13 → 수리 후 **0**.

## 2. 이번 commit에서 수리한 것

- DTO 분리: `NewstartKnowledgeItemResponse` / `...CreateRequest`(중복 `authority` 제거)
  / `...UpdateRequest` / `...ListResponse`, `NewstartEmpiricalCaseResponse` / `...ListResponse`
  / `...SearchRequest` / `...StatsResponse`
- `NewstartKnowledgeStatsResponse` **신규** — source commit f268c7c에도 타입이 없었고
  호출부만 존재했다. 필드 구성은 builder 호출부 + 프론트(`admin_newstart_wiki.js`) 계약으로 복원.
- `NewstartKnowledgeService.parseDate`, `NewstartDataIngestionService.buildCaseEntity`
  동일하게 — 호출만 있고 정의가 없는 메서드였다(`EasyImportService.parseDate` 미러).
- Repository: `JpaSpecificationExecutor` 2개 추가, derived 쿼리 파라미터 String → enum
  (`KnowledgeStatus`/`SourceType`). 런타임 400의 원인.
- ADMIN 런타임 결함 2건: `/wiki`의 `size` 하드코딩(`page, 20, sort` → `page, size, sort`),
  principal `getUsername()` → `loginId()`.
- 신규 테스트 `NewstartOfficialReviewIngestMySqlTest`
  (`@EnabledIfEnvironmentVariable(NEWSTART_WIKI_MYSQL_TEST)`, 기존 repo 패턴 준수)

## 3. 최종 검증 결과

- `compileJava` 100 → 0 errors
- 단위 suite `tests=5 skipped=0 failures=0 errors=0`
- MySQL suite `tests=5 skipped=0 failures=0 errors=0 time=149.625`
- 실제 적재(로컬 테스트 DB): REVIEW 82행, **ACTIVE 0**, CONTRADICTS 관계 1, UNKNOWN 13,
  2회차 ingest 멱등(skip) 확인
- ADMIN `/stats` → `{"totalKnowledge":82,"reviewCount":82,"activeCount":0,...}`
  `/wiki?size=100&status=REVIEW` → total 82 / 반환 82 / UNKNOWN 13 / CONFLICT_REVIEW 2
- Top10 차주 질문 10/10 ANSWERABLE (DB 행 기준)
- 전체 suite 회귀: newstartknowledge 도메인 실패 **0**. 실패 57건은 전부 AcademyLms/Chart/symlink
  등 타 도메인이며, 변경 클래스를 `domain/newstartknowledge/` 밖에서 참조하지 않음을 grep으로 확인.

MERGE 없음 / DEPLOY 없음 / PRODUCTION DB 변경 없음 / ACTIVE 승격 없음 / AI Router 미변경.

## 4. HOME에서 결정해야 할 것 (본인 권한 밖이라 미조치)

1. ~~**V983 버전 충돌(필수 조정)**~~ → **해결됨**: main에 EASY `V983__add_easy_collection_foundation.sql`
   과 profile `V984__add_newstart_member_profiles.sql` 이 먼저 merge되어 최고 적용 번호가 V984가
   되었다. 이 branch의 knowledge migration은 미적용 파일이므로
   `V985__add_newstart_knowledge_wiki.sql`로 renumber했다(SQL 내용 무변경, 파일명/version만).
   `docs/WORKFLOW.md` §4 "적용 전 V_파일은 직접 수정 가능" 준수.
   ⚠ 로컬 DB에 이미 구 V983 knowledge를 적용한 사람은 Flyway가 "resolved migration not found"로
   실패할 수 있다 — 새 로컬 DB로 재시작하거나 `flyway_schema_history` 정리(운영 DB 절대 금지).
2. `frontend stats.knowledgeGaps` — 백엔드가 계산하지 않아 항상 0 표시. 필드를 임의로 만들지 않았다.
3. `detectGap` 은 retrieval 경로에 `// 예시` placeholder로 남아 있음(§12 소관 외).
4. UNKNOWN/CONFLICT 목록 배지 UI 없음(상세 값/tags substring으로만 확인 가능) — 신규 UI scope.
5. `NewstartKnowledgeService.search` 의 `buildSpec`/`buildPageable` 이중 계산 — 무해, 방치.
