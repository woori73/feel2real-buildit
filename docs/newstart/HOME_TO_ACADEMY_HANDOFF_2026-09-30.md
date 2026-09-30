# HOME → ACADEMY HANDOFF — NewStart (2026-09-30 13:05 KST 작성)

작성 위치: HOME. 학원 PC에서 설명 없이 이어 작업할 수 있는 상태를 목표로 한다.
담당: Qoder = NewStart 전담. **EASY / LMS / CHOONGGI / 시험지 파일·DB는 건드리지 않는다** (EASY는 OpenCode 담당).

## [DEPLOY]

- PR: **#444** — https://github.com/woori73/feel2real-buildit/pull/444 (MERGED, merge 커밋 방식)
- PRODUCTION SHA: **a0f65f849609d08c832a0e96b96cfbc689c7e6d**
- AUTO DEPLOY: **SUCCESS** (run 36664627042, event=workflow_run, headSha=a0f65f84, job build-and-deploy success 03:33:48Z)
- LIVE URL: https://feel2real.co.kr/newstart

정정·운영 방식 확인(2026-09-30 사용자 지시): NewStart 프로덕션은 **며칠 전부터 의도적 자동 배포** 방식이다.
`main Build Check SUCCESS → Deploy to EC2 workflow_run 자동 실행`이 정상 운영이다.
**POLICY DRIFT가 아니고, 수동 배포 전제로 재조사하지 말며, workflow를 수정하지 말 것.**

## [GOLDEN-1] (질문 원문 고정 — 원문 그대로 사용할 것)

> 폐업 후 보증금 받은거 부실 차주 갈때 통장거래내역에서 문제 삼지 않나요? 예전엔 잔고만 확인하고 거래 내역은 확인 안한다는 글이 많은데 7월부터 강화 된 후 어떻게 되는지 궁금합니다.

- 3차 실행: **DONE — FAIL (조건 7 정상 시간)**
- 118 retrieval (2024 실제 경험): **NOT CONFIRMED** (3차에서 답변 미수신) / 2차 LIVE에서 답변·근거 노출 **YES** 확인됨
- 119 retrieval (2026 최근 사례): **NOT TESTED (3차)** — 2차 LIVE에서 sources 탈락 확인 → 원인 코드 수정(a6c02460) 배포 완료, LIVE 미검증
- OFFICIAL retrieval: **YES** (evidenceCount=12, systemPromptChars=5462로 컨텍스트 조립 확인)
- ANSWER ORDER: **NOT TESTED** (3차 답변 없음)
- EVIDENCE UI ("이 답변의 근거"): **NOT TESTED** (3차 답변 없음)
- SOURCE CLASS (119가 "신복위 카페 사례"로 오분류되지 않는지 = a6c02460 LIVE 검증): **NOT TESTED**
  - 근거: unit `classifyCafeCaseWithoutUrlUsesAuthorityCategory` PASS, `orderedEvidence_keepsApprovedCafeKnowledgeWithinSources` PASS — LIVE 미확인
- ragMs: **52–77ms** (서버日志 `newstart ai chat timing ragMs=… evidenceCount=12`)
- llmMs: **180,004 / 180,003 → ok=false (2건)**, 다른 1건 **125,864 → ok=true answerChars=784**
- totalMs (클라이언트측): **180,187ms → HTTP 504**
- 미확인 단정("7월부터 강화"): 3차 답변이 없어 판정 불가 → **NOT TESTED**

### 남은 문제 (학원이 착수할 지점)

1. **LLM provider 지연이 GOLDEN-1의 실질 blocker**다. ragMs는 정상(52–77ms)이고 evidence 12건 컨텍스트 조립도 정상이며,
   `llmMs`가 transport 상한으로 보이는 **180,003/180,004ms에서 끊기고 ok=false**. 같은 질문·같은 evidence에서
   125,864ms에 ok=true로 784자 답변이 나온 이력이 있으므로 retrieval 문제가 아니라 **provider/transport 타임아웃 변동**으로 추정된다.
   → LIVE 3차 재측정은 **LLM이 빠른 시간대에 재시도**해야 의미가 있다.
2. 클라이언트 504(게이트웨이)와 서버 180s 상한이 같은 값에서 겹친다. 상한이 어디 정의인지 확인 필요
   (`NewStartLlmClient`/transport read-timeout, 또는 EC2 앞 프록시). 단, **타임아웃 값올리는 수정은 LIVE 재측정 실패 원인 분리와 회귀 테스트 근거를 갖춘 뒤에** 진행(억지 PASS 금지).
3. GOLDEN-1 **8조건 중 아직 미확인**: 조건 1(119 retrieval)·2(118 retrieval)·4(시간차 설명)·6(1차 사례 우선)·7(정상 시간)·8(근거 영역 구분 표시).
   **하나라도 FAIL이면 COMPLETE 보고 금지** (기존 게이트 유지).

## [UI QA] (배포 a0f65f84 기준)

- DESKTOP: **PARTIAL PASS** — https://feel2real.co.kr/newstart 실측(12:47 KST): 잔존 금지 라벨 `NewStart AI`/`지피지기 AI`/`Jipijigi AI` **0건**,
  장식 영어 라벨 `MY DEBT`/`GUIDE`/`STEP`/`LATEST` 등 **0건**, `지피지기 Agent` **4회** 노출.
- 390px MOBILE: **NOT TESTED**
- OLD AI LABEL: 잔존 없음 (desktop 실측)
- ENGLISH LABEL: 장식 라벨 없음 (desktop 실측). 페이지 title: `새출발기금 부실차주 | 내 채무조정 상황을 한곳에서 관리`
- **미확인 화면**: A to Z, 핵심 실전 도구(fss-helper), 최신정보, 채무상황(Credit4U), 정보 공유 채팅 — 코드 정적 가드 테스트만 PASS(87/87), LIVE 전수 미확인
- 예외 유지(억지 번역 금지): Credit4U, NewStart·Feel2Real, `Agent` 고유명칭, `금감원 민원 Agent` 도구명, 기능 설명·개인정보 고지 문장 속 "AI"

## [DATA QUALITY]

- FAQ duplicate URL: **미확인 (HANDOFF)** —症状만 기록됨:
  `https://www.newstartfund.or.kr/https://www.newstartfund.or.kr/Faq/List.do?...` (base URL 중복 접두사)
  → 원인(seed payload vs 저장 시 접두)과 대상 knowledgeId **전부 미확인**. READ ONLY로 `newstart_official_updates`/knowledge의
    official 항목 sourceUrl 전수 조회 후 원인 특정. 수정은 원인 확인 후에만.

## [KNOWLEDGE]

- **118**: ACTIVE — OPERATOR_KNOWLEDGE, 2024 기준선(폐업사실증명만 제출, 임대차·보증금 반환자료 요구 없음),
  tags에 잔고/거래내역/통장/재산심사, relatedRuleIds=119
- **119**: ACTIVE — CAFE_ARTICLE, 2026-08 관찰(심사 중 임대차·보증금 반환현황 개별 보완요구),
  tags에 **OPERATIONAL_CHANGE_SUSPECTED, OFFICIAL_CHANGE_UNCONFIRMED**, sourceUrl="" (빈 URL은 의도적), relatedRuleIds=118
- relation: **118 ↔ 119 상호 연결. 두 사례를 같은 차주로 병합하지 말 것** (서로 다른 시점의 별개 사례)
- 승인 근거 reason: 경험/관찰 진술이지 규정이 아님, 빈 URL 의도적 — ADMIN 화면 승인 이력에 기록됨
- 총 ACTIVE = 119건
- **k13~k42 (30건): REVIEW 후보 문서 상태로 유지** — `docs/newstart/research-official/ccrs/k13..k42_*.json`
  (기관 지식 보강: 신복위·신용보증/Credit4U/금감원/법원 공식자료, URL·확인일 보존).
  **DB 미적재.** 억지 ACTIVE 승인 금지. 정상 ADMIN ingest/review flow로만.
  - 표현 안전장치 유지: "2026년 7월부터 공식 규정이 변경됐다" / "8월부터 심사가 강화됐다"로 확정 금지.
    "과거와 다른 보완요구 사례가 최근 확인됨 + 공식 제도 변경 여부는 별도 확인 필요" 수준.
  - 사실 정정 유지: 지급명령 이의 = 2주, lawhelp ≠ 공단, 추심법 제9조, 장기연체 = 3개월·100만원.

## [CODE]

- LATEST MAIN: **a0f65f84** (= PR #444 merge 커밋)
- 관련 commit: `a6c02460` (GOLDEN-1 2차 FAIL 원인 수정) → `a0f65f84` (PR #444 merge)
  그 외 PR #444 포함 6커밋: 743a6938, 4ef2cca7, 044c8d6d, 3c3fd696, 80442164, a6c02460
- WORKTREE: **CLEAN** (`git status --porcelain` 출력 없음)
- UNCOMMITTED: **없음**
- UNPUSHED: **없음** (브랜치 `feat/newstart-single-agent-ui-20260930` HEAD a6c02460 push 완료, main에 머지됨)
- 로컬 브랜치: `feat/newstart-single-agent-ui-20260930` (PR #444 머지로 반영 완료 — 새 작업은 main 기준 브랜치에서 계속)
- 검증 근거(로컬): NewStartAiChatServiceTest 11/11, JipijigiResearchServiceTest 19/19,
  NewstartRepairApprovalRagMySqlTest 4/4(환경 없으면 skip, 실패 0), `node --test src/test/js/newstart_*.test.js` 87/87,
  PR CI Build Check PASS(3m7s), main Build Check SUCCESS

## [NEXT EXACT ACTION] (학원에서의 순서)

1. **production GOLDEN-1 3차 재실행(1회만, 병렬 금지)에서 119 evidence/출처 노출부터 확인한다.**
2. ragMs/llmMs/totalMs를 서버日志(`newstart ai chat timing`)와 클라이언트 totalMs 양쪽에서 재측정 기록하고,
   llmMs=180,003 상한 재현 여부를 판단한다(재현 시 transport 타임아웃 원인 분리).
3. GOLDEN-1 8조건 전체 PASS 확인 후 desktop + **390px** 영어 라벨/Agent 명칭 LIVE QA 전수(미확인 6개 화면 포함).
4. FAQ 중복 URL 원인 READ ONLY 특정 → 대상 knowledgeId 보고.
5. k13~k42를 정상 ADMIN ingest/review flow로 적재(승인 전후 상태 확인, 직접 SQL 금지) → 기관 RAG 4문항 검증.
