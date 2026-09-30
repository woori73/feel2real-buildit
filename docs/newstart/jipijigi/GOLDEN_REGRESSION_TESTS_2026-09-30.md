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
