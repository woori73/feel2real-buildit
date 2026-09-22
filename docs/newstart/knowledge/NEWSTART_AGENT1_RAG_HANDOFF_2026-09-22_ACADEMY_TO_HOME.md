# Agent1 RAG HANDOFF — ACADEMY → HOME (2026-09-22, emergency checkpoint)

- CURRENT BRANCH: `feat/newstart-canonical-rag-20260922` (base `origin/main` 1cd88f64)
- LAST COMMIT: `683233ba` wip(newstart): checkpoint canonical RAG integration
- PUSHED HEAD: `683233ba623ce1ea9daad89cc2af8767bb4ccb78`
- REPOSITORY: woori73/feel2real-buildit (fetch+push origin, no force)

- CANONICAL SOURCE: `docs/newstart-canonical-integration-20260922`
- CANONICAL HEAD: `4b807bd7`
- CANONICAL INPUT: PASS (5 files read: POLICY + FAQ 130 + CASE 62 + RELATIONS 186+3 + HANDOFF)
- POLICY LOADED: YES (no reinterpretation; class javadoc points to policy doc as source of truth)

## COMPLETED (code, untested)

1. NEW `newstartknowledge/service/NewstartCanonicalAnswerPolicy.java`
   - SYSTEM_PROMPT_ADDENDUM (evidence layers / absence rule / case-not-rule / judgment label / no-verdict-engine / TRUE GAP / conflict coexistence)
   - EvidenceBundle + composeEvidenceBlock (fixed section order, empty sections omitted, all-empty → UNKNOWN only)
   - classifyCaseOutcome / isUnresolvedStatus / containsJudgmentLabel / containsForbiddenVerdictPhrase
2. EDIT `NewstartKnowledgeRetrievalService.java` (+77): `findRelatedCasesRegardlessOfOutcome`,
   `findSimilarCasesRegardlessOfOutcome` (no finalReductionRate predicate; existing methods untouched)
3. EDIT `NewstartKnowledgeRelation.java` (+11): RelationType += SUPPORTED_BY / PARTIALLY_SUPPORTED_BY /
   RELATED_CASE / RELATED_FAQ / UNEXPLAINED_BY_OFFICIAL / CONFLICT_REVIEW (VARCHAR(30) fits, migration 불필요)
4. EDIT `NewStartAiChatService.java` (+23/-3): `askWithEvidence(question, evidenceBlock)` 추가.
   `ask()`는 evidence null로 위임 → 기존 호출부·기존 테스트 동작 불변
5. NEW tests (DB 없음, Mockito/JUnit): `NewstartCanonicalAnswerPolicyTest` (A–G + 자동검증 6종),
   `NewstartKnowledgeRetrievalCaseOutcomeTest` (미확정 사례 통과 ×2 + relation 어휘 ×1)
6. ANSWER CONTRACT 정렬: 기존 8-section 계약(TOPIC/OFFICIAL/OPERATOR/EBOOK/CAFE-CASE/DIFFERENCES/
   FOLLOW-UP-UNKNOWN/SOURCE-LINKS) 유지 + REPEATED_CASE_PATTERN/UNEXPLAINED_BY_OFFICIAL/UNKNOWN 표현 추가.
   기존 계약 폐기 없음

## IN PROGRESS (중단 시점)

- `./gradlew compileJava compileTestJava` 실행 중이었으나 약 20분간 진행 없어 수동 중단.
  컴파일 성공 여부 미확인. 테스트 미실행. bootJar 미실행

## FILES CHANGED (commit 683233ba, 6 files, +672/-3)

- M `.../newstart/service/NewStartAiChatService.java`
- M `.../newstartknowledge/entity/NewstartKnowledgeRelation.java`
- M `.../newstartknowledge/service/NewstartKnowledgeRetrievalService.java`
- A `.../newstartknowledge/service/NewstartCanonicalAnswerPolicy.java`
- A `.../newstartknowledge/service/NewstartCanonicalAnswerPolicyTest.java` (test)
- A `.../newstartknowledge/service/NewstartKnowledgeRetrievalCaseOutcomeTest.java` (test)

## TEST STATUS

- compile/test command가 약 20분간 진행 없이 정지되어 수동 중단함
- 화면의 AcademyVideoServiceTest deprecated warnings는 warning이며 이번 NewStart 변경의 실패 판정으로 간주하지 않음
- NewStart unit tests: NOT RUN

## IMPORTANT

- EASY 변경 없음. CHOONGGI 변경 없음. 내신Wiki 변경 없음
- Canonical source files 수정 없음
- Production DB write 0. ACTIVE 일괄 승격 없음. Migration 파일 추가 없음 (ingest는 코드+계획까지만, 미착수)

## NOT VERIFIED

- compileJava / compileTestJava 통과 여부
- 신규 테스트 2종 + 기존 테스트(NewStartAiChatServiceTest, NewstartKnowledgeRetrievalActiveOnlyTest) 결과
- bootJar
- Controller에 retrieval→askWithEvidence 배선 (미구현, 다음 단계)

## NEXT STEP AT HOME (집 PC)

1. `git fetch origin --prune`
2. `git checkout feat/newstart-canonical-rag-20260922` (또는 worktree add)
3. `./gradlew compileTestJava` 먼저 (짧게). 통과하면 아래만 실행:
   `./gradlew test --tests "*newstartknowledge*" --tests "*newstart.service.NewStartAiChat*"`
   (전체 test 금지 — @SpringBootTest가 MySQL을 요구함)
4. 실패 시 원인 분석 후 수정 → checkpoint commit → push. 성공 시 bootJar 후 RAG QA로 진행

- MERGE: NO
- DEPLOY: NO
- PRODUCTION DB WRITE: 0
