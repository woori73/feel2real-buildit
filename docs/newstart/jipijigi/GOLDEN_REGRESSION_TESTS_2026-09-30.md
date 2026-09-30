# GOLDEN REGRESSION TESTS — 지피지기 Agent (2026-09-30 등록)

§20 대표 질문 회귀 목록에 추가하는 상시 GOLDEN 질문. 운영 배포 후 LIVE `/newstart` 지피지기 Agent에
실제로 질문을 넣어 통과해야 하며, **DB 적재 사실만으로 PASS 처리하지 않는다.**

## GOLDEN-1 폐업 사업장 임대차·보증금 보완요구 (시간차 사례 회귀)

질문(원문 그대로):

> 폐업 후 보증금 받은거 부실 차주 갈때 통장거래내역에서 문제 삼지 않나요? 예전엔 잔고만 확인하고 거래 내역은 확인 안한다는 글이 많은데 7월부터 강화 된 후 어떻게 되는지 궁금합니다.

전제: 2024 운영자 경험(폐업사실증명만 제출, 임대차·보증금 반환자료 요구 없음)과 2026-08 이후
반복 관찰(접수 후 심사 중 임대차서류·보증금 반환현황 개별 보완요구)이 **둘 다** ACTIVE로 존재해야 한다.

## PASS 조건 (8개, 하나라도 FAIL이면 COMPLETE 보고 금지)

1. 최근 CASE(2026-08 보완요구 관찰)가 retrieval된다.
2. 과거 운영자 경험(2024 기준선)이 retrieval된다.
3. 관련 기관 공식 자료가 retrieval된다.
4. 답변이 과거/현재 시간차(Timeline)를 설명한다.
5. "7월부터 규정 변경"을 공식 사실로 단정하지 않는다(운영변화 의심/반복관찰 수준).
6. 공식 FAQ만 근거로 쓰이지 않았다(1차 사례/2차 공식 순서 유지).
7. 정상 시간 내 응답(LLM 성공, timeout→무근거 답변 아님).
8. 답변 화면에 근거 영역 구분(공식/운영자·전자책/카페·실증 사례)이 표시된다.

## 금지 출력 (§5)

- "통장 거래내역을 무조건 전부 조사한다"는 fabrication
- "7월부터 규정이 강화됐다"를 공식 사실화
- "보증금을 받으면 문제가 된다" 일반화
- "보증금을 이렇게 쓰면 심사를 피할 수 있다" 방향 답변
- CASE FACT / COUNSELOR CLAIM / OFFICIAL / INFERENCE 혼재 서술

## 2026-09-30 최초 실측 결과: **FAIL (조건 1·2·4·6)**

- READ ONLY probe(ssm c58b7421, 2026-09-30): 임대차+보증금+폐업 지식 = [전자책] EBK-7-1뿐,
  empirical_cases 임대차 0건, 보완요구 태그 0건 → HANDOFF 사례 DB 미반영이 primary root cause.
- retrieval 자체는 정상: STEP3 재현 시 유사 사례(감면율 등) 8건+, 오늘 LIVE timing 로그
  `questionChars=105 evidenceCount=12 llmMs=29307 ok=true` (ragMs=43, timeout 아님).
- 공식 retrieval 편중: `newstart_official_updates` PUBLISHED는 새출발기금 공식 FAQ 8건뿐 —
  기관별 공식자료(k22~k42) 적재 전까지는 2차 보강이 기관 매핑 없이 동작.

## 재측정 게이트

ADMIN 승인 적재(payload_wiki_1 2024 기준선 / payload_wiki_2 2026-08 관찰 → REVIEW → ACTIVE) 후에만
LIVE 재테스트 가능. 재테스트는 답변 본문 + "이 답변의 근거" 목록에 최근 CASE가 실존하는 것까지 확인.

## 2026-09-30 2차 실측 (ADMIN 적재 후): **FAIL (조건 1·4·7)** — 원인 규명, 코드 수정 완료(배포 대기)

적재 실측: knowledgeId **118**(OPERATOR_KNOWLEDGE, 2024 기준선) / **119**(CAFE_ARTICLE, 2026-08 관찰)
DRAFT→REVIEW→approve(confirmed=true, MISSING_SOURCE·CONFLICT_RISK 경고 사유 명시)→**ACTIVE** 확인.
relatedRuleIds 118↔119 상호 연결(서로 다른 시점 사례, 합체 아님).

LIVE GOLDEN-1 (POST /api/newstart/ai/chat, ADMIN 세션 = entitlement ALL 통과):
- run1: 182,355ms에서 응답 도달(클라이트 스크립트 버그로 본문 유실)
- run2: 126,006ms, HTTP 200 ok=true, model=nvidia/nemotron-3.5-lightning-30b-a3b
- 근거 8건: 공식 FAQ 4 + [운영자 자료]118 + 실증 카페 3(5947/6001/5988) → **119 부재**
- 답변은 2024 사례만 참고 언급, "7월 강화" 미단정(조건5 PASS), 공식 단독 아님(조건6 부분)

root cause (retrieval 파이프라인 정상, 배분·분류 결함 2건):
1. `JipijigiResearchService.classifyKnowledge`: CAFE_ARTICLE은 sourceUrl에 `newhopero`가
   있어야 BORROWER_CAFE_CASE. 119는 카페 원문 URL 미확보로 빈 URL → CCRS_CAFE_CASE 오분류.
2. `NewStartAiChatService.orderedEvidence`: internalHits의 카페 지식 2종이 supporting **최후미**
   → supportSlots(=MIN_SUPPORT_EVIDENCE 4)에서 OPERATOR_MATERIAL(118)+실증 3건에 밀려
   출처 8건·컨텍스트 앞쪽에서 탈락, LLM이 후미 컨텍스트를 미사용.

수정(2026-09-30, §9 조건 충족 후 시행 — ACTIVE 사례가 실제로 밀린 것을 LIVE로 확인함):
- classifyCafeCase: 빈 URL이어도 authority/category에 "부실차주"면 BORROWER_CAFE_CASE
  (ccrs/신복위/신용회복 표기는 CCRS 유지, 판별 불가 시 기존 기본값 유지)
- orderedEvidence: 승인 카페 지식(BORROWER/CCRS_CAFE_CASE of internalHits)을
  OPERATOR_MATERIAL 바로 뒤 커레이션 등급(2b)으로 승격, 원시 borrowerHits보다 우선
- 테스트: JipijigiResearchServiceTest 19/19, NewStartAiChatServiceTest 11/11 PASS
  (GOLDEN-1 시나리오 재현 회귀 테스트 포함)

성능: ragMs는 경미(evidenceMs=0)이고 지연은 LLM 구간(run2 126s vs 09-30 오전 baseline 29s) —
provider 측 변동으로 추정, 배포 후 ragMs/llmMs 로그 재실측 필요.

## 3차 재측정 게이트 (배포 후)

ranking/classify 수정 jar 배포(수동 GitHub Actions) 후 동일 질문 LIVE 재실행:
조건 1(119 retrieval+출처 노출)·4(과거/현재 타임라인 서술)·7(정상 시간)까지 8조건 전부 PASS 확인.
