# EASY 소득세 에이전트 — 2026-09-25 (설계·구현 기록)

## SOURCE OF TRUTH

- 실제 신고값: repo에 없음 (전 항목 MISSING — §ACTUAL FILING MATERIAL).
- 장부: EASY journal (ACADEMY 657 + PUBLISHING 12 posted, 운영 실측).
- 세법 구조 참고: OpenAccountants kr-income-tax Skill v2.0 (코드 미복사, 수치 미검증 사용).
- 공식 세율·서식: 신고 시점 국세청/홈택스 재확인 필요 (2026 귀속 → 2027-05 신고 패턴).

## ACTUAL FILING MATERIAL

- ACTUAL_FILED_VALUE: 0건 (수입·필요경비·소득·공제·세액 전 항목 MISSING).
- EASYSHOP_REFERENCE: 테스트 기대값 상수만 (2024/2025/2026 사업장별 수입·비용).
- TEST_FIXTURE: 400,000원대 mock (신고값 아님).
- easy_filing_references: 구조만, seed·실행 없음.

## OPENACCOUNTANTS KR FINDINGS

- repo 실재, Skill v2.0 (tax_year 2025, source-cited draft, 세무사 검증 대기).
- LICENSE: 코드 AGPL-3.0 / 가이드 Guide License v1.0 — 복사 없음.
- USE: 계산 흐름(총수입→필요경비→사업소득→공제→과세표준→산출→공제→기납부→결정→지방 10%), 입력 목록, 거부 카탈로그, 보수적 기본값.
- NOT COPIED: 코드·수치 (UNVERIFIED 참조로만 등록).

## OFFICIAL TAX SOURCES

- 2025 귀속 신고: 2026-05-01~06-01 (nts.go.kr, korea.kr 확인).
- 2026 귀속: 2027-05 신고 패턴 — 세율·서식·모두채움은 신고 시 OFFICIAL VERIFICATION REQUIRED.
- 홈택스 로그인: 공동·간편·금융인증 (간편인증 human-in-loop 적합).

## CURRENT EASY CAPABILITY

- 재사용: filingCheck (독립 비교), incomeTax (사업장별+합산, PREPARING), filing_references (수동 등록),
  ReferenceYearSnapshot (불변 기준), tax_classification 6종, TB/P&L/B/S journal-source.
- 세액 계산 없음 (코드 명시) — 본 Phase도 확정세액을 계산하지 않는다.

## THREE-WAY GAP MATRIX (요약)

| 항목 | 실제 신고 | EASY | OA+공식 |
|---|---|---|---|
| 사업장별 수입·필요경비·사업소득 | MISSING | 장부 집계 가능 | 구조만 (2025 draft) |
| 합산 사업소득 | MISSING | 합산 조회 가능 | 구조만 |
| 소득공제~지방소득세 | MISSING | MISSING (PREPARING) | 2025 구조, 미검증 |
| 신고 시점 공식 확인 | — | — | 2027-05 재확인 필요 |

## MULTI-BUSINESS MODEL

- ACADEMY 사업소득 + PUBLISHING 사업소득 → 개인 단계 합산 (DB 합산 금지).
- PRIMARY = ACADEMY. 납세자 정보는 데이터로 전달 (실명 hard-code 금지).

## TAX ADJUSTMENT MODEL

- BOOK(원장 불변) → 조정(DEDUCTIBLE/NON_DEDUCTIBLE/REVIEW_REQUIRED/NOT_APPLICABLE, 근거·출처 기록)
  → BUSINESS TAXABLE INCOME → PERSONAL AGGREGATION.
- 예: 장부비용 100, 불인정 20 → 필요경비 80 (장부는 100 유지).

## HISTORICAL REPLAY RESULT

- 신고값 부재 → 전 항목 MISSING_INPUT (정상). MATCHED 0, 추측 계산 0.
- 분류기: MATCHED/MISSING_INPUT/RULE_MISMATCH/SOURCE_MISMATCH/ROUNDING_DIFFERENCE/
  MANUAL_TAX_ADJUSTMENT/UNKNOWN.

## MISSING DATA

- 실제 신고서·결정문 전 항목, 소득공제·세액공제·기납부 자료, 2026 귀속 검증済み 세율,
  출판사 매출측, 은행 원본 (기존 gap과 동일).

## IMPLEMENTED COMPONENTS

- EasyTaxRuleRegistry (연도별 규칙 + 검증 상태, 미검증 차단).
- EasyBusinessTaxComputation (사업장 사업소득, 조정 불변 보장).
- EasyPersonalTaxAggregation (합산, primary 검증, 세액 단계 차단).
- EasyTaxReplayService (재현 분류).
- 마이그레이션 없음.

## TEST RESULT

- EasyIncomeTaxEngineTest 신규 (분리·합산·불변·연도·차단·replay).
- 기존 GL/TB/P&L/B/S/filing-check/income-tax 회귀 유지.

## NEXT STEP

1. 실제 신고서 확보 → filing_references 등록 → replay MATCH 검증.
2. 2026 귀속 세율 신고 시점 공식 확인 → VERIFIED 등록 → 세액 단계 개방.
3. 은행·매출측 자료 → 필요경비·수입 완성 → HOMETAX-READY dataset.
