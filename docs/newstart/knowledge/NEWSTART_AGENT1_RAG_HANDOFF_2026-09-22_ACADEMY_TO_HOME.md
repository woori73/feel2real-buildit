# AGENT1 — RAG INTEGRATION HANDOFF (ACADEMY → HOME) 2026-09-22

- 작성: 학원 PC 세션 (canonical integration 직후 이어서, 21:45 KST hard stop 규정 하)
- 상태: **구현 미착수. audit + handoff까지 완료.** 코드 변경 0건.

## 1. SOURCE OF TRUTH

- Canonical branch: `docs/newstart-canonical-integration-20260922`
- Canonical HEAD: **`4b807bd7`** (READY FOR AGENT1: YES)
- 산출물 5식: `docs/newstart/knowledge/`
  - `NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md` ← **필독 (Mandatory RAG Answer Policy)**
  - `NEWSTART_CANONICAL_FAQ_2026-09-22.json` (130) / `NEWSTART_CANONICAL_CASE_2026-09-22.json` (62) / `NEWSTART_CANONICAL_RELATIONS_2026-09-22.json` (186 rel + 3 patterns + C-01~C-04)
  - `NEWSTART_CANONICAL_HANDOFF_2026-09-22.md`
- 검증 10항목 전부 0 PASS (parse/dup/dangling/provenance/PII/승격/NOT_FOUND오독/삭제/보편화/과대포장)

## 2. AGENT1 BRANCH

- `feat/newstart-agent1-rag-integration-20260922` (전용 branch — 타인 branch에 섞지 않음)
- base: `4b807bd7` 그대로 (canonical 파일 5식이 이 branch에 이미 존재, `git ls-files docs/newstart/knowledge/` 확인 완료)

## 3. LAST PUSHED COMMIT

- 본 문서 commit 후 push 완료 시점의 SHA는 git log에서 확인 (branch remote tracking)

## 4. COMPLETED (오늘)

1. Canonical integration 전체 완료 (FAQ/CASE/RELATIONS/HANDOFF/POLICY, 4 commits c4b989d0→4b807bd7, push됨)
2. Policy 확인: "CLASSIFY FIRST. INFORM SECOND. JUDGE ONLY WHEN ASKED." 원문 존재 확인
3. Agent1 전용 branch 생성 (4b807bd7 기반)
4. **RAG architecture audit 완료** (§9)

## 5. IN PROGRESS

- 없음. audit 직후 handoff 모드로 전환 (21:40 규정 준수).

## 6. NOT STARTED

- retrieval integration (NewStartAiChatService ↔ NewstartKnowledgeRetrievalService 연결)
- answer contract integration (SYSTEM_PROMPT → policy 기반 빌더)
- canonical JSON ingest (REVIEW 전용)
- regression tests (audit-기반 신규 테스트 일체 미작성)

## 7. FILES CHANGED (본 branch, origin/main 대비)

- 코드: 0건
- docs: canonical 5 files (4b807bd7에서 이미 포함) + 본 handoff 파일 1 (총 6 files, +10,586줄 규모)

## 8. TEST STATUS

- 실행 완료(오늘, 별도 Phase 1 코드): discovery 단위테스트 25 green (XML 확인) — canonical branch와 무관
- bootJar: 실행 안 함 (21:40 이전 종료 불가 판단 → 미시작, 규정 준수)
- 실행 못 한 것: RAG regression tests(미작성), 전체 suite, bootJar

## 9. IMPORTANT FINDINGS (audit — path는 worktree `feel2real-agent4` 기준, feel2real-buildit 동일)

패키지 prefix: `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/`

1. **Knowledge 도메인 실존** (`newstartknowledge/`) — 그러나 `newstart/service/NewStartAiChatService.java:9-12` Javadoc은 "Knowledge DB/RAG 없다"고 주장 → **stale 문서**, 수정 대상
2. **ACTIVE-only 문제: YES (의도된 설계)** — `NewstartKnowledgeItem.java:73` status 기본 DRAFT, enum `KnowledgeStatus` (line 110: DRAFT/REVIEW/ACTIVE/ARCHIVED). retrieval 필터: `NewstartKnowledgeRetrievalService.java:48,80,118,145`, `NewstartKnowledgeItemRepository.java:27,32` (`WHERE k.status='ACTIVE'`). 승격은 `NewstartKnowledgeService.approveReview` (line 249-269) 수동 경로만.
3. **ingest는 전부 REVIEW 강제** — `NewstartCuratedReviewIngestService.java:111,194` (ebook), `NewstartOfficialReviewIngestService.java:290`, "ACTIVE 생성 경로 없다" (line 27 코멘트). 스케줄러 없음, admin trigger만 (`NewstartIngestionAdminController.java:23-39`).
4. **answer 조립 지점**: `NewStartAiChatService.java:24-28` `SYSTEM_PROMPT` 하드코딩 → line 45-46 `WikiAiRouterClient.complete()`. **현재 retrieve()가 이 경로에 연결되어 있는지 audit에서 미확인 — 첫 작업으로 ask() 본문 정독 필요.**
5. chat WebSocket 경로(`NewStartChatService`, `NewStartChatWebSocketHandler`)는 RAG와 별개 — 건드리지 말 것.

### canonical integration 방식 (설계 확정 전 제안)
- canonical JSON → 신규 `NewstartCanonicalIngestService`(NewstartCuratedReviewIngestService 형제, **status=REVIEW 강제**) + `/ingest/canonical` admin endpoint
- relations/conflict 정보는 NewstartKnowledgeItem에 1:1 매핑 어려움 →最初は policy 전문(prompt 접두) + FAQ/CASE item화, relations는 retrieval 후보정 또는 v2 유보
- **ACTIVE 승격(approveReview)은 오늘도 금지** — canonical READY와 무관하게 QA 후 결정

## 10. NEXT EXACT STEP (집 PC 첫 작업)

1. `NewStartAiChatService.ask()` 원문 정독(5분): retrieval 미연결 확인 시 →
2. **retrieval integration**: `NewstartKnowledgeRetrievalService.retrieveForQuestion(...)`을 ask()에서 호출하도록 연결 + SYSTEM_PROMPT를 policy preamble 빌더로 교체 (`NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md` §1,§4,§6 문구 그대로)
3. 그 다음 canonical ingest service(REVIEW 강제) + regression 테스트 순서

## 11. COMMANDS (집 PC)

```powershell
git clone https://github.com/woori73/feel2real-buildit.git "C:\Users\<user>\Documents\GitHub\feel2real-agent1"  # 이미 worktree/repo 있으면:
git fetch origin --prune
git checkout feat/newstart-agent1-rag-integration-20260922
git log -3 --oneline   # head가 §3 SHA와 일치 확인
# canonical 재확인:
git show 4b807bd7 --stat
```
- 경로 공백 주의(따옴표), `&&` 금지, `;` 사용. gradle 긴 작업은 JDK21: `JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'` (기존 memory 참조)

## 12. SAFETY

- production DB write: **0** / merge: **NO** / deploy: **NO** / ACTIVE 승격: **NO**
- PR #365/#366/#368/#370 전부 OPEN 유지 (touch하지 않음)
- Knowledge DB ingest: 본 세션에서 실행하지 않음 (설계 제안만)
