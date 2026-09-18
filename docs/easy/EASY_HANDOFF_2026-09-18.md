# Easy 실데이터 작업 인계 — 2026-09-18

> 집 PC 작업 종료 시점 인계 문서. 학원 PC에서 이어서 작업한다.
> EasyLedgerService 코드 수정 없음 / 운영 API POST·DELETE 없음 / DB 변경 없음 /
> deploy 없음 / 운영 서비스 변경 없음 상태에서 작성했다.

## 현재 운영 상태

- EasyShop 실데이터 CSV 총 18개
- 2개 사업장 × 2024/2025/2026 × SALES/EXPENSE/DEPOSIT_WITHDRAWAL
- 전체 원본 6,223행 (로컬 검증済み)
- 원본 CSV 수정 금지

ACADEMY 2026 SALES:

- 총 102행 (로컬 검증済み)
- 학원 SALES는 사용자 회계 기준상 전부 수강료
- EASYSHOP:매출 -> INCOME_TUITION(accountId=1) 확정
- 운영 mappingId=1 등록 완료

## CSV 변환기

csv_to_json.py의 기존 문제:
snake_case CSV header를 DTO camelCase로 변환하지 않아
ledger_type -> ledgerType 누락 등으로 staging에서 "거래일 오류"가 발생했음.

원인:
실제 날짜 오류가 아니라 DTO field mapping 오류.

날짜:
2026-01-03 정상
UTF-8 정상
BOM 없음
컬럼 밀림 없음

현재 변환 규칙/수정 상태:

- `csv_to_json.py` (repo root, 본 인계에 함께 commit)가 수정済み 버전이다.
- `FIELD_MAPPING` 25개: CSV snake_case header -> `EasyShopImportRequest.EasyShopRow`
  camelCase field 1:1 명시 매핑. 예: `ledger_type -> ledgerType`,
  `total_amount -> totalAmount`, `item_desc -> itemDesc`,
  `receipt_method -> receiptMethod`, `pay_method -> payMethod`,
  `biz_reg_no -> bizRegNo`, `tax_exempt_supply -> taxExemptSupply`,
  `deposit_amount -> depositAmount`, `withdrawal_amount -> withdrawalAmount`,
  `account_category -> accountCategory`, `item_type -> itemType`,
  `menu_path -> menuPath`, `business_code -> businessCode`,
  `fiscal_year -> fiscalYear`, `card_fee -> cardFee`, `ref_no -> refNo`.
- DTO에 없는 CSV 컬럼(`source`, `source_data_type`, `balance`)은
  `raw` JSON에 원문 보존하고 DTO field로는 보내지 않는다.
- `businessCode`는 강제 주입(ACADEMY), `date`는 `YYYY-MM-DD` canonical 검증
  (`date.fromisoformat` round-trip), 값 변경 금지(검증 assert 포함).
- 기본 동작은 첫 행 1건만 payload로 출력한다
  (`build_first_row_request`). 102행 전체 출력 모드는 없다.
- 검증済み: 단일 행 payload, DTO 키 25개 정확 일치, sales/expense/funds
  매핑, 빈 값·따옴표 쉼표 정상 처리.
- 원본 CSV는 수정하지 않는다. commit 대상에서 제외한다.

## 운영 시험 기록

batchId=1:

- 최초 3행 시험
- ERROR 3
- 원인: DTO field mapping
- transaction 생성 없음
- 그대로 보존 (삭제·confirm 금지)

batchId=2:

- 첫 행 1건 시험
- rowId=4
- 2026-01-03
- amount=540000
- READY=1 / ERROR=0
- confirm 완료
- txnId=1 생성
- accountId=null
- journal 없음

주의:
540,000원은 2026년 학원 전체 매출이 아니다.
첫 번째 시험 거래 1건일 뿐이다.

현재 txnId=1은 운영에 남아 있음.

## account mapping

운영:
mappingId=1

EASYSHOP:매출
-> INCOME_TUITION
-> accountId=1

사용자 확정 회계 정책:
ACADEMY SALES는 모두 수강료 매출로 처리.

## DELETE 500 확정 원인

DELETE /api/admin/easy/transactions/1 실행 시 HTTP 500.

실제 운영 stack trace 확보 완료 (Diagnose f2r-app workflow, run 35302136369,
2026-09-17 19:34:51 UTC 로그).

ROOT EXCEPTION:
org.springframework.dao.InvalidDataAccessApiUsageException

CAUSE:
org.hibernate.TransientPropertyValueException

근본 원인:
EasyLedgerService.deleteTxn에서

1. 삭제 대상 txn을 참조하는 EasyCorrectionLog 저장
2. 같은 persistence context에서 txnRepository.delete(txn)

순서로 수행.

flush 시 EasyCorrectionLog.txn이 REMOVED transaction을 참조하여
Hibernate TransientPropertyValueException 발생.

DB FK 위반 아님.
SQL 실행 전 Hibernate flush 단계에서 실패.

관련:
EasyLedgerService.java:240-247
EasyLedgerController.java:77
EasyCorrectionLog.java:40-42

rollback 결과:

- txnId=1 유지
- correction log 없음
- journal 없음

## 수정 후보

migration 불필요.

최소 코드 수정 후보:
EasyLedgerService.deleteTxn 1곳.

스냅샷을 먼저 확보한 후
transaction 삭제/flush 후 correction log 저장,
또는 삭제 correction log에서는 txn 참조를 null 처리하고
snapshot JSON으로 감사 정보를 보존.

아직 구현하지 않음.
학원 PC에서 코드와 테스트를 다시 확인한 후 수정한다.

## 진단 workflow

PR #309 MERGED
Diagnose f2r-app read-only workflow 추가.

PR #311 MERGED
조회 범위 2h -> 12h
1000 -> 5000 lines
exception head 300 -> 600

이를 통해 운영 DELETE 500 stack trace 확보 성공.

## 다음 작업 순서

학원 PC에서:

1. main 최신 pull
2. 이 인계 문서 읽기
3. EasyLedgerService.deleteTxn 최소 수정
4. 회귀 테스트 작성
5. 반드시 수정 전 실패 / 수정 후 PASS 확인
6. 별도 PR 생성
7. 사용자 확인 후 merge/deploy
8. 운영 txnId=1 공식 DELETE 재시험
9. txnId=1 정상 제거 + correction log 확인
10. ACADEMY 2026 SALES 첫 행 신규 staging
11. accountId=1 확인
12. 1행 confirm
13. transaction 540000 확인
14. journal debit=540000 / credit=540000 확인
15. P/L revenue=540000 확인
16. /easy 2026년 1월 수입 540000 확인
17. E2E PASS 후에만 나머지 ACADEMY 2026 SALES 101행 진행

아직 나머지 101행 및 다른 17개 CSV import 금지.

## Publisher 주의

2026 Publisher 자료는 완전한 연간 자료가 아님 (로컬 검증済み).

- SALES ~ 2026-05-06
- EXPENSE ~ 2026-05-04
- DEPOSIT_WITHDRAWAL ~ 2026-05-07

화면/보고서에 반드시
"이지샵 확보분 ~5/7"
등 cutoff를 명시해야 함.
