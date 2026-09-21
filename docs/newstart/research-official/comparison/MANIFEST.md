# 부실차주 선택경로 비교 DB — 영구 매니페스트 (REVIEW)

- 수집일: 2026-09-21 (UTC). 영구 경로: `docs/newstart/research-official/comparison/`
- 기존 `ccrs/`(신복위 중개형 부실우려)와 분리된 비교 전용 구조. 중복 아님.
- 전건 STATUS=REVIEW, auto_active=false. ADMIN 승인 전 ACTIVE 사용 금지.
- Production DB / AI Router / UI 변경 없음.

## 파일 (15건)

- `concepts.json` — 부실차주 상태 vs 제도 분리. `부실차주=새출발기금` 저장 금지.
- `evidence_ns_property_2026.json` (EV-NS-PROP-2026) — FSC 2026-06-25: 변제가능률 정의, 30~80% 차등, 가상자산('26.1)·비상장주식('26.5), 사해행위, 신용정보법('26.8.13)
- `evidence_ns_property_historical.json` (EV-NS-PROP-HIST) — 2022 기준(60~80%, 순부채). 현재 규칙을 과거로 덮어쓰지 않음.
- `evidence_pw_property.json` (EV-PW-PROP) — 협약 제4조⑤(배제 3·5·9호), 제21조(상각/미상각). 월변제금 산식·감면계수는 UNKNOWN.
- `evidence_debt_scope.json` (EV-DEBT-SCOPE) — 협약 제5조 채권범위 + 새출발기금 협약대출 한정 + FAQ 타제도 권고.
- `evidence_telecom.json` (EV-TELECOM) — 제5조①6·7호(2025.09.04 신설), 통합채무조정(90%·10년·다음날 추심중단), 단말기할부 UNKNOWN.
- `evidence_routing.json` (EV-ROUTING) — 1회 제한, 부실우려→부실 재조정, 취소 후 3개월, 기존이용자 제외. PW→NSF는 UNKNOWN.
- `evidence_credit_info.json` (EV-CREDIT-INFO) — PW 1101 / 중개형 1802, 1년 성실 시 해제, 신속·사전·중개형(부실우려) 미등록.
- `property_matrix.json` (MX-PROPERTY) — 14개 재산항목 × 양제도. 공식 근거 없는 항목 UNKNOWN.
- `debt_matrix.json` (MX-DEBT) — 15개 채무 조사상태. 라우팅 규칙 아님(REVIEW).
- `comparison_matrix.json` (MX-COMPARE-14) — 14항목 비교표, 셀→evidence 연결.
- `cases_scaffold.json` — CASE A~E 구조만. CAFE CASE 전건 NOT_COLLECTED, 합성 금지.
- `top10_questions.json` — 실제 차주 질문 10개 + 필요 knowledge/evidence. 답변 미생성.
- 본 MANIFEST.md

## UNKNOWN / CONFLICT 목록

- UNKNOWN: 임차보증금·보험/해약환급금·배우자/공동소유(양제도), 가상자산·비상장주식(PW 측), 단말기할부금, 건보료 등 비전기 공과금, PW 월변제금 산식·감면계수, PW→NSF 전환 공식 근거, 새출발기금 신청기한(2차 출처만 존재).
- CONFLICT_REVIEW: ccrs/ k02 10일/31일 하한 (본 DB와 별개 유지).
- 시점분리: 2022(60~80%) vs 2026 현행(30~80%) — HISTORICAL 분리. 통신채무 2024-02 이전 vs 2025.09.04 이후 분리.

## 2026 CURRENT RULES VERIFIED

- FSC 2026-06-25 보도자료(fsc.go.kr/no010101/87188): OFFICIAL_CURRENT.
- 협약 2025-09-04 개정 반영본(제5조 통신 3호 신설 등): OFFICIAL_CURRENT.
- CCRS MENU_ID=620 (공공정보), 1930 (개인워크아웃): 2026-09-21 확인.

## 단정 금지 준수

- `재산→특정제도 추천` 없음. 재산이 각 제도의 어느 단계(지원여부·감면율·신청배제·심사자료)에 어떻게 영향을 미치는지만 기록.
