# Easy 이지샵 데이터 이전 계획 (문서만, importer 미개발)

> 실제 이지샵 CSV/Excel 파일을 확보한 뒤 별도 작업으로 importer를 만든다.
> V974 기반(staging·매핑·규칙)이 준비되어 있다. 실제 import는 아직 하지 않는다.

## 현재 준비된 기반 (V974)

- staging: `easy_import_batches` + `easy_import_rows` — 원본은 대기, 확정분만 장부로.
- 매핑: `easy_account_mappings` — 외부 코드 ↔ 내부 계정 ↔ NTS 표준 (표준은 확정 전).
- 자동분류: `easy_classification_rules` — 거래처/적요 패턴 + 우선순위 + 금액구간.
- 내보내기: `GET /api/admin/easy/transactions/export` (CSV BOM, 세무사 전달 기초 형식).
> 이 문서는 현재 스키마(V973)가 이지샵 항목을 받을 수 있는지 점검한 매핑표다.

## 매핑표 (이지샵 → Easy)

| 이지샵 항목 | Easy 저장 위치 | 비고 |
|---|---|---|
| 거래일 | `easy_transactions.occurred_on` (DATE) | 엑셀 날짜 → YYYY-MM-DD 정규화 필요 |
| 수입/지출 | `easy_transactions.direction` (INCOME/EXPENSE) | 이지샵 표기(입금/출금 등) 매핑표 별도 정의 |
| 금액 | `easy_transactions.amount` (BIGINT, 원) | 쉼표/원 제거 후 정수 파싱, 음수 거부 |
| 계정/분류 | `easy_transactions.category` (VARCHAR 50) | 이지샵 계정과목 → Easy 분류 매핑표 별도 정의 |
| 거래처 | `easy_transactions.counterparty` | |
| 적요 | `easy_transactions.description` | |
| 결제수단 | `easy_transactions.pay_method` | 이지샵 수단명 → Easy 표기 매핑 |
| 사업체 | `easy_transactions.business_id` (FK) | **이지샵 파일에 사업체 구분이 없으면 행별 규칙(적요 키워드 등)으로 배정 — 규칙은 이전 실행 전 확정** |
| 비고/메모 | `easy_transactions.memo` | |
| 증빙 | `easy_transactions.receipt_ref` | v1은 참조 텍스트만 (파일 import 대상 아님) |

## 원장·수납 매핑

| 이지샵 항목 | Easy 저장 위치 | 비고 |
|---|---|---|
| 학생명/학교/학년/연락처 | `easy_students` | 동명이인 해소 규칙 필요 (학교+학년+연락처 조합 권장) |
| 월 수강료 청구 | `easy_tuition_payments` (billed) | 대상월 YYYY-MM 정규화 |
| 입금 내역 | `easy_tuition_payments` (paid) | 동일 (학생, 사업체, 월) 중복은 하나로 합산 후 등록 |

## 이전 원칙

1. 사업체 미지정 행은 절대 import하지 않는다 (학원/출판사 섞임 방지).
2. 중복 실행해도 2중 등록되지 않게 `(student, business, month)` UNIQUE + 거래는 (일자, 금액, 적요) 사전 대조 후 수동 확인.
3. 금액 파싱 실패 행은 건너뛰지 않고 오류 목록으로 보고한다.
4. importer는 Admin 전용 일회성 도구로 만들고, 실행 로그(건수·실패 목록)만 남긴다.
