# EASY 12:00 Checkpoint — 2026-09-26 Morning (READ-ONLY)

> 구현 없음. 조사 결과 저장용. main @78e3de8e, PR #418 OPEN (CI PASS).

## 2026 장부 (운영 실측 재사용)

- ACADEMY 657 / PUBLISHING 12, 전건 POSTED·분류·provenance, journal 균형.
- Shinhan/KB: COLLECTED BUT NOT IMPORTED (파일 타 PC, KB 코드 미머지).
- 은행 원본: 없음. 수납 0건, 카드이벤트 0건. B5-36 미해결.

## 2026 예상세액 구조

- 계산 가능: 사업장별 사업소득 (journal TB 기반, 조정 분리).
- 차단: 검증済み 세율 없음 → 세액 단계 REVIEW_REQUIRED (정상).
- 부족: 인적공제(부양/배우자/자녀), 연금/건보, 기납부/원천, 세액공제, 신고서 대조값 — 개인프로필 엔티티 없음 (RRN 등 저장 없음, 현상 유지).
- 2026 귀속 세율: 신고 시(2027-05) 공식 확인 필요.

## PR #418 재사용 가능

- BusinessTaxComputation / PersonalTaxAggregation(데이터 지정 primary) / TaxRuleRegistry(연도·검증게이트) / TaxReplayService / filing-check / income-tax API — 재구현 불필요.

## 학생/KPI foundation

- 원시값: 학교·학년·enrolledOn·상태·수납(billed/paid). 집계: 상태별 인원·학생별 합산만.
- MISSING: 학교/학년별 집계, 신규·재등록·재원기간·cohort, 채널/반복매출 구분.
- KPI: 매출·손익·재학생수 계산 가능. 예상세액·준비금·학년예측·온라인/반복/BuildIt/NewStart/가맹/대표노동 구분 — NEEDS DATA (가짜 숫자 금지).

## 오후 학원 준비 (실행 금지, 설계만)

- HOMETAX: 필요 자료(카드매출·현금영수증·세금계산서·원천/기납부), 간편인증 human-in-loop (AI는 대기·재개만), 공식 Excel/CSV 다운로드 → repo 밖 work-input → sha256 → staging → dedupe → PENDING. 인증정보 저장 금지, COLLECTED BUT NOT IMPORTED로 둠.
- CREFIA(여신협): 승인/매입/입금 3종 (APPR/PUR/DEP externalId), 동일 경제매출 중복수익 금지 (fail-closed 유지, 승인 후 활성화). 이용기관 승인·사업자번호 필요.
- 공통: 원본 보존·provenance·fingerprint·관리자 승인 후 전기. 마감기간 확인 후 작업.

## 학원 지참/확인 목록

- VIEW LIFE PC 2 XLS (USB/사설 이동, GitHub 금지)
- EasyShop 18 CSV 위치 (EASYSHOP_DATA_DIR)
- 실제 신고서 (filing_references 수동 등록용)
- 간편인증 가능 휴대폰 (홈택스용)

## 상태

- CODE CHANGE: NO / PR: NO (신규 연구 PR 없음) / PRODUCTION WRITE: NO / MERGE: NO / DEPLOY: NO
- 미커밋 문서: 본 파일 + BANK/CARD 감사 2건 (다음 작업 시 정리)
