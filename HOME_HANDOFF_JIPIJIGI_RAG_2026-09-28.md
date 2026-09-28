# HOME HANDOFF — Jipijigi RAG answer quality (2026-09-28 close)

## 0. 상태 스냅샷 (실측)
- worktree: `C:/Users/VIEW LIFE/feel2real-wt-jipijigi-rag-intent`
- branch: `feat/jipijigi-rag-intent-20260928` (this branch only, nothing merged)
- base HEAD = origin/main = `09ee3b3e` (Merge PR #435) — branch had **no commits yet** at close
- 작업 트리: 2 modified + 3 new files (아래 4번), 미커밋 → 이 handoff와 patch로 보존

## 1. 오늘 완료한 것
### 1-1. 부실차주 Wiki REVIEW 27건 전건 승인 (Phase 1, 종료)
- 운영 화면 `https://feel2real.co.kr/admin/newstart/wiki`의 정상 승인 경로
  `POST /api/admin/newstart/wiki/review/{id}/approve` (사유 필수, 경고 있으면 `confirmed=true`)로 27/27 승인.
- SQL bulk update, 삭제, 신규 등록, 재수집, 기존 ACTIVE 90건 수정 없음. **production DB write = 승인 27건뿐.**
- 최종 상태(사용자 화면 확인 + API 재확인 일치): **TOTAL 117 / ACTIVE 117 / REVIEW 0 / DRAFT 0**, 실제 사례 11.
- read-only 후속 확인: 승인 27건은 `JipijigiResearchService`의 ACTIVE 지식 검색 범위에 도달한다
  (STEP1 spec = `status=ACTIVE` + title/content/tags/category LIKE). 단 STEP1 단계당 절취량은
  `PER_STEP_LIMIT=8`이라 117건이 한 질의에 모두 실리지 않는다(혼잡도 이슈는 아래 3번에 미포함으로 남음).

### 1-2. Phase 2 루트코인 확정 (HEAD 코드 + 운영 데이터 read-only)
대표 질문(고정 회귀 fixture)에 대해 현재 답변이 제도 일반론부터 시작하는 원인을 코드로 특정:
1. `keywordsOf()`가 토큰 12개로 잘라 **결정적 사건어(기한이익상실·이자·연체)가 질의에서 dropout**
   (남은 예: 것을/알고/처리되는/담보대출은 — 활용형이라 LIKE 불일치).
2. STEP1 ACTIVE 지식은 `createdAt DESC` + `PER_STEP_LIMIT=8` 절취 → 최신 글이 슬롯을 선점.
3. `orderedEvidence()`는 의도와 무관하게 **공식 우선 하드코딩**.
4. 사건/의도 모델 없음, 위험(충돌·출처없음) 메타데이터가 검색 신호로 미사용.
5. 운영 데이터 실측: 승인 축적 답변 세션(`jipijigi_answer_sessions` accepted) **0건**,
   수집 실증 사례 107row, 게시 공식 업데이트 43row. **실증 사례 어디에도 "기한이익상실" 문구 없음,
   "후순위 담보" 연속구 없음** → 대표 질문은 "정확히 같은 사례 없음" 정직 경로가 실제 경로다.

## 2. 현재 수정 중인 것 (미완료, WIP)
의도 해석 + 사건 중심 검색 + 경험 우선 리랭크 + 위험 인식 리랭크를 `JipijigiResearchService` 쪽에 넣는 중.

- **NEW** `domain/newstart/jipijigi/service/JipijigiIntent.java`
  `POLICY / EXPERIENCE / ACTION / EXPERIENCE_ACTION / MIXED` + `experienceFirst()`
  (POLICY·MIXED = 공식 우선 유지 → 기존 계약 보존, EXPERIENCE·ACTION·EXPERIENCE_ACTION = 사례 우선).
- **NEW** `.../JipijigiQuestionFrame.java`
  문장 분할로 `KNOWN_CONTEXT / NEW_EVENT / ACTUAL_NEED / EVENT_TERMS` 분리, 의도 판정,
  사건 중심 다중 질의 생성(`searchQueries`), `exactEventMatch()`(같은 사건 판정),
  `alignmentBonus()`(같은 사건·채무유형·단계·기관), `hasActionRecord()`,
  없는 사례 고지 문장 상수 `EXPERIENCE_NOT_FOUND_MESSAGE = "현재 지피지기 DB에서는 정확히 같은 사례를 찾지 못했습니다."`
  `legacy(keywords)` 프레임은 기존 `keywordHits+최신순` 계약을 그대로 유지한다(기존 테스트 보호).
- **NEW** `.../JipijigiEvidenceScorer.java`
  어휘 가중치표(사건어 4~7, 상태/기관/유형 2, 일반 주제 1) + **부분어 축약**(후순위담보가 후순위/담보를 흡수),
  공백 제거 매칭("후순위 담보"="후순위담보"), 리스크 감점표
  `CONFLICT_RISK 6 > MISSING_SOURCE 3 > UNKNOWN_RISK 2 = HISTORICAL 2 > CASE_DERIVED 1 = UNCLEAR_AUTHORITY 1`,
  `riskCodes(item)`은 `NewstartKnowledgeService.computeWarnings` 재사용(**migration 없음, 기존 필드 파생**).
- **MOD** `.../JipijigiResearchService.java`
  - `Evidence`에 `matchedTerms / riskCodes / score` 추가(감사 로그 요구 12 대응).
  - `ResearchPack`에 `intent / frame / exactExperienceFound` 추가.
  - STEP1: 후보 창 `KNOWLEDGE_CANDIDATE_WINDOW=80`으로 넓히고 적합도→키워드일치→최신순으로 재정렬 후 8건 절취.
    **저장소 호출은 단계당 1회 유지**(기존 `times(1)` 검증 테스트 보호).
  - STEP2/STEP3/STEP4도 같은 랭킹 파이프라인(`Ranked` record + `topRanked`)으로 통일.
  - 의도가 사례/경험 계열이면 내부 ACTIVE 근거가 3건 이상이어도 STEP2 외부 사례조사를 생략하지 않는다.
  - gapNotes에 `INTENT=... | 사건어=... | 사건 질의=...` 기록(AFTER 감사 로그의 출발점).
- **MOD** `domain/newstartknowledge/service/NewstartKnowledgeService.java`
  `computeWarnings(item)`를 `public static`으로 변경(유일한 변경. 호출부는 인스턴스 참조 그대로라 소스 호환).

## 3. 아직 미완료인 것 (집에서 이어서)
1. `NewStartAiChatService.java` **무손질** — 요구 7·9·10이 미실현:
   - `orderedEvidence(pack)`가 `pack.intent`로 공식우선/경험우선을 분기(null이면 기존 공식우선).
   - 의도별 답변 정책 SYSTEM_PROMPT 블록(경험 우선 5단 구조, 알려진 사실 반복 금지,
     "공식 안내에 따르면" 시작 금지, 준비물 나열 금지, "신복위에 문의하세요" 단독 마감 금지),
     `exactExperienceFound=false`면 정직 문장 강제, `CONFLICT_RISK` 단독 확정 금지 가드레일.
   - `buildEvidenceContext()`에 `riskCodes` 주의 라벨 출력.
2. BEFORE/AFTER 검색 감사(운영 데이터 read-only) 미실행 — 브라우저에 projection/scoring 하네스가
   일부 남아 있었으나 결과 정본은 없음(재실행 권장, 아래 NEXT TASK 2).
3. 신규 자동화 테스트 전량 미작성(요구 14). 기존 테스트는 실행조차 안 함.
4. 실제 LLM E2E(요구 15) 미시도. `POST /api/newstart/ai/chat`은 `requireThanksMember` Gate →
   admin 세션으로는 403 가능성. 회원(THANKS_MEMBER) 신원 또는 로컬 클론 LLM 호출로 전환 필요.
5. 대표 질문 회귀 fixture(요구 11) 미등록.
6. ID 33/101 "10일 vs 31일" CONFLICT_RISK 단독 확정 방지 검증 미완료.
7. 죽은 오버로드 2개 잔여(마감 중 안전지점 보존): `searchCcrsKnowledgeItems(List)`,
   `searchOfficialUpdates(List, ...)` — 의도 해석 없이 legacy 프레임으로 위임한다. 정리 대상.

## 4. 변경 파일 (git status --short 기준)
```
 M src/main/java/.../domain/newstart/jipijigi/service/JipijigiResearchService.java
 M src/main/java/.../domain/newstartknowledge/service/NewstartKnowledgeService.java
?? src/main/java/.../domain/newstart/jipijigi/service/JipijigiEvidenceScorer.java
?? src/main/java/.../domain/newstart/jipijigi/service/JipijigiIntent.java
?? src/main/java/.../domain/newstart/jipijigi/service/JipijigiQuestionFrame.java
```
diffstat: 2 files changed, ~236 insertions / ~61 deletions + 신규 3파일.

## 5. TEST STATUS
- **NOT RUN**: 전체 테스트 스위트(`JipijigiResearchServiceTest`, `NewStartAiChatServiceTest`,
  `NewstartKnowledgeReviewWorkflowTest` 포함) — 한 번도 실행하지 않았다.
- **PASS/FAIL 미확인**: 마감 직전 `./gradlew compileJava compileTestJava --offline` 백그라운드 실행만 건드림.
  결과가 확인됐다면 그 결과만 신뢰하고, 확인되지 않았으면 "미컴파일 상태"로 취급할 것.
- 정적 점검으로 확인한 것: 삭제/개명 심볼(`searchActiveKnowledgeRanked`, `toKnowledgeEvidence`,
  `evidenceScore` 시그니처)의 dangling reference 없음, `computeWarnings` 외부 호출부 소스 호환.
- 기존 테스트가 지켜야 할 계약(코드로 재확인):
  `keywordsOf`/`classifyKnowledge`/`searchEmpiricalCases(List<String>)`(6010이 first, 8건 절취),
  단계당 repo 호출 1회(`times(1)`), `ask_ordersOfficialBeforeCafeCases`의 공식<운영<카페 순서
  → POLICY/미판정 경로가 그대로 공식우선이어야 통과.

## 6. 신규 ACTIVE 27건의 risk/source 경고 다루는 중(요구 8)
- 경고는 스키마 컬럼이 아니라 **기존 필드에서 파생되는 코드**다(CONFLICT/충돌, UNKNOWN/미확인/미해결,
  sourceUrl 없음, OFFICIAL_HISTORICAL, DERIVED_RULE+relatedCaseIds, authority 없음).
- 진행: `JipijigiEvidenceScorer.riskCodes(item)` → `Evidence.riskCodes`에 실고, 점수에서 감점
  (clean > 출처확인 > UNKNOWN > MISSING_SOURCE > CONFLICT 순서가 되도록).
- **미완료**: 감점만 있고 라벨이 아직 프롬프트에 안 나간다(NewStartAiChatService 무손질).
  `33/101` 항목의 10일/31일 충돌이 실제로 CONFLICT_RISK 코드로 잡히는지, 그리고
  "충돌 근거 단독 확정 금지"가 답변 문장에서 지켜지는지는 미검증.

## 7. PER_STEP_LIMIT=8 관련
- **값 변경 없음.** `PER_STEP_LIMIT=8` 그대로다(단계당 결과 상한 = 기존 계약·테스트 유지).
- 바꾼 것은 후보 창: STEP1 `KNOWLEDGE_CANDIDATE_WINDOW = 8*10 = 80`(신규),
  STEP3/STEP4 `CASE_CANDIDATE_WINDOW = 8*20 = 160`(기존 160 → STEP4 공식에도 처음 적용).
- 즉 "8건 절취"는 유지하되 **어떤 8건을 고르느냐**를 최신순→적합도순으로 바구는 중이다.

## 8. PR #436 overlap (중요)
`fix/newstart-preopen-access-latency-20260928` (OPEN) 변경 파일 중 이 작업의 **다음 편집 대상과 겹치는 것**:
- `domain/newstart/service/NewStartAiChatService.java` ← 요구 7·9·10을 여기에 넣는다 → **충돌 확정적**
- `domain/newstart/controller/NewStartAiChatController.java` (응답 DTO에 intent를 실을 계획이면 충돌)
- `domain/newstart/llm/NewStartLlmClient.java`, `NvidiaNimLlmTransport.java`, `application.yaml`
  (reasoning_budget 1024·latency 실측 — E2E 재현 시 이 쪽 설정이 기준)
- `docs/API.md` (문서에도 겹침)
- 이 브랜치가 **아직 건드리지 않은 파일들**이라 지금 커밋에는 충돌 없음.
  단 PR #436 merge **전에** `NewStartAiChatService`를 이 브랜치에서 편집하면 재병합 시 충돌·의미 충돌
  (SYSTEM_PROMPT 문장, MAX_* 상수)이 생기므로: #436 merged 후 origin/main 당겨서 편집 시작할 것.
  merge/rebase는 지시대로 하지 않는다(fetch 후 신규 base에서 재적용 방식으로 결정).
- semantic overlap: #436는 접근성·grant·latency가 목적이고 근거 순서는 건드리지 않는다.
  단 `SYSTEM_PROMPT`의 출력 계약 문장(기존 테스트가 `contains("사용자에게 바로 보여도 되는 최종 문장만 한국어로")`
  를 검증)을 둘 다 만지면 의미가 충돌할 수 있다.

## 9. HOME FIRST TASK (정확한 다음 순서)
1. `git -C "C:/Users/VIEW LIFE/feel2real-wt-jipijigi-rag-intent" status --short && git log --oneline -1`
   그리고 `git diff HEAD~1` (또는 patch)로 WIP 확인 → **첫 동작은 compile/test 실행**:
   `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot" && ./gradlew compileJava compileTestJava`
   → 실패하면 위 3번 항목 7의 dead overload/시그니처부터 정리.
2. `JipijigiQuestionFrameTest` 신규(의도 5분류, 대표 질문의 KNOWN/NEW_EVENT/ACTUAL_NEED/EVENT_TERMS 추출,
   사건 질의에 "후순위 담보 기한이익상실"류가 들어가는지) → `JipijigiResearchServiceTest`에
   사건 중심 랭킹·위험 감점·CONFLICT 단독 확정 방지 추가(기존 테스트 수정 금지, 추가만).
3. PR #436 머 여부 확인(`gh pr view 436 --json state,mergeCommit`) → merged면 origin/main fetch 후
   이 브랜치에 새 base 재적용, 미erged면 `NewStartAiChatService` 편집은 보류하고 랭킹·테스트만 진행.
4. 그 다음 `NewStartAiChatService`에 intent 분기 + 답변 정책 + risk 라벨 구현,
   `ask_ordersOfficialBeforeCafeCases` 등 기존 공식우선 테스트는 POLICY 경로로 통과 유지.
5. 운영 DB **READ ONLY**로 BEFORE/AFTER top12 감사(refType/refId/sourceClass/title/matched/risk/score) —
   admin 화면 `GET /api/admin/newstart/wiki?status=ACTIVE`, `/cases`, 공식 updates 재사용.
6. E2E는 회원(THANKS_MEMBER) 세션 또는 로컬 클론 LLM. NVIDIA 호출은 요구사항상 금지 상태(확인 후 진행).

## 10. 안전선 (지켜진 상태)
- 운영 DB write: **0** (Phase 1의 정상 승인 27건 이후 추가 write 없음)
- UI/FSS Agent/Credit4U/질문게시판/다른 Agent 작업: **변경 없음**
- merge: **NO**, deploy: **NO**, 추가 Wiki 승인: **NO**, 추가 NVIDIA 호출: **NO**
- 재수집/재ingestion/지식 추가/삭제: **NO**
