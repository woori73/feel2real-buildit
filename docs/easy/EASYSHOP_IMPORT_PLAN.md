# Easy 이지샵 데이터 이전 (V976 실동작)

> `POST /api/admin/easy/imports/easyshop` (source_kind=EASYSHOP).
> 원본 row는 staging에 보존하고 확정분만 장부로 옮긴다. 확정 시 분개가 함께 생성된다.

## 컬럼 계약 (Claude 추출 CSV → JSON rows)

| JSON 필드 | 의미 | 필수 | 정규화 |
|---|---|---|---|
| `date` | 거래일 | ✅ | `YYYY-MM-DD`, `YYYYMMDD`, `YYYY.MM.DD` 허용 |
| `direction` | 수입/지출 | ✅ | 수입·입금·매출·수익 → INCOME, 지출·출금·비용·매입 → EXPENSE (그 외 ERROR) |
| `amount` | 금액 | ✅ | 쉼표·원 제거 후 정수, 0 이상 (음수 ERROR) |
| `counterparty` | 거래처 | 선택 | trim |
| `description` | 적요 | 선택 | trim |
| `payMethod` | 결제수단 | 선택 | trim |
| `accountName` | 이지샵 원본 계정명 | 선택 | 확정 매핑 우선 → 규칙 → 미분류(review). 추측 매핑 없음 |
| `businessCode` | 사업체 코드 | 조건부 | `ACADEMY`/`PUBLISHING` (대소문자 무시). 없으면 요청 `businessId`, 둘 다 없으면 전체 거부 |
| `refNo` | 이지샵 거래번호 | 선택 | `raw_data`에 `[ref:...]`로 원본 보존 |
| `raw` | 원문 | 선택 | `raw_data`에 그대로 보존 |

## 이전 원칙

1. 사업체 미확정 행이 1건이라도 있으면 staging 전에 전체를 거부한다 (일부 적재 후 실패 없음).
2. 사업체 코드 혼합 파일은 사업장별 배치로 자동 분리 staging한다 (추측 배정 없음).
3. 장부에 같은 fingerprint 거래가 있으면 해당 행은 `DUPLICATE`로 확정 제외한다 (재입력 방지).
4. 외부 계정명은 `external_account`에 원문 보존하고 `easy_account_mappings` 확정 매칭만 자동 적용한다.
5. 확정 시 `sourceKind=EASYSHOP`, `sourceRef=파일명#row-N`, fingerprint를 기록하고 분개를 생성한다.
6. 2024/2025/2026 혼합 입력 가능 — 귀속은 `occurredOn` 기준, 사업장·연도 isolation은 조회 scope가 보장한다.

---

## 이지샵 데이터 이전 계획 (기존 매핑표 보존)

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

---

## V976 신규 사항

- **sourceKind 분리**: `EASYSHOP` (매출/비용) / `EASYSHOP_FUNDS` (입출금 자금이동)
- **환불 역분개**: 음수 비용은 절댓값 변환 없이 `is_expense_reversal` 플래그 + 방향 INCOME으로 보존
- **자금 분리**: 입출금은 P&L에서 제외 (`EASYSHOP_FUNDS` scope 분리)
- **확정 매핑 우선**: `external_account` 원문 보존 + `easy_account_mappings` 확정 매칭만 자동 적용
- **중복 방지**: 장부 중복 fingerprint → `DUPLICATE` 확정 제외
- **대응 비용 탐지**: `EASYSHOP_FUNDS` 출금 중 비용 대응 없는 거액 출금 → `MISSING_EXPENSE` 리뷰 생성 (임의 분류 없음)
- **데이터 커버리지**: `dataThrough` 기준일 반환으로 2026 불완전 데이터 오해 방지