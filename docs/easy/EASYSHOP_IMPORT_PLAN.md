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
