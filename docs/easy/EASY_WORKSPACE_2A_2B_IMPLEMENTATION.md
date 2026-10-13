# Easy Workspace 2A·2B 구현 계약

## 범위

- 관리자 전용 내부 파일럿이다.
- 면세 학원 운영자의 수강료·교재비·환불·미수금과 강사료·임차료·운영비 흐름을 우선한다.
- 일반 과세사업자 부가세 신고 기능, AI 자동 승인, OCR, 자동 신고는 추가하지 않았다.
- DB 스키마, Flyway, 거래 삭제/취소/VOID 정책은 변경하지 않았다.

## API

모든 Workspace API는 `/api/admin/easy/workspace` 아래에 있고 `ROLE_ADMIN`과 필수
`businessId`를 요구한다.

| API | 역할 |
|---|---|
| `GET /monthly?businessId=&month=yyyy-MM` | 월 금액·업무 상태·데이터 원천 |
| `GET /transactions?businessId=&from=&to=` | 사업체 복합 조건 거래 조회 |
| `GET /transactions/{txnId}?businessId=` | 사업체 복합 조건 단건 조회 |
| `POST /transactions` | 사업체·기간 검증 후 기존 거래 저장/분개 재사용 |
| `PUT /transactions/{txnId}?businessId=` | 원 사업체 잠금 및 원·대상 기간 검증 후 수정 |
| `GET /reviews?businessId=&kind=&status=` | 사업체별 검토함 |
| `POST /reviews/{reviewId}/approve?businessId=` | 잠금 후 승인/제외 |
| `POST /reviews/{reviewId}/hold?businessId=` | 무변경 보류 확인 |

## 계산형 상태

| 화면 상태 | 데이터 원천 | 의미 |
|---|---|---|
| `COLLECTED` | `easy_transactions.source_kind` 및 월 수집 원본 | 외부 출처가 보존된 거래 |
| `IMPORT STAGED` | `easy_import_batches.status=STAGED` | 아직 장부 확정 전인 배치 |
| `LEDGER SAVED` | `easy_transactions` | 장부에 저장된 거래 |
| `JOURNAL POSTED` | `easy_journal_entries.status=POSTED` | 활성 분개가 존재하는 거래 |
| `OPEN/REVIEW/CLOSED` | `easy_annual_closings` | 회계기간 상태; 행이 없으면 `OPEN` |

금액은 기존 `EasyLedgerService.summary`를 재사용하므로 세무구분 손익 제외와 비용 환불
역분개 정책이 유지된다. 분개는 기존 `EasyJournalService`를 그대로 호출하며 `postLegs`
복식분개 정책을 재작성하지 않는다.

## 승인 원자성·멱등성

- `EasyReviewItem`을 `PESSIMISTIC_WRITE`로 잠근다.
- 검토 승인, 거래 수정, 정정 로그, 재분개, 검토 종료는 단일 `@Transactional` 범위다.
- 이미 처리된 검토는 승인 요청을 재식별할 키가 없으므로 항상 HTTP 409다.
- 사업체 이동은 거래일 귀속연도의 원 사업체와 대상 사업체가 모두 `CLOSED`가 아닌지
  확인한다.
- 보류는 상태나 장부를 변경하지 않는다.

## 화면

- `/easy`: 사업체별 월 대시보드와 처리 단계/데이터 원천
- `/easy/transactions`: 사업체 필수 수입·비용 장부와 거래별 계산 상태
- `/easy/reviews`: 유형별 검토함
- `/easy/evidence`: 증빙 검토 화면
- `/easy/accounting`: 기존 POSTED 분개 기반 시산표·균형 감사 연결

## 검증 명령

Windows PowerShell에서 저장소 루트 기준:

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '..\gradle-user-home')
.\gradlew.bat test --tests "*EasyWorkspaceServiceTest" --tests "*EasyAuditServiceTest" --tests "*EasySecurityWebMvcTest" --tests "*EasyJournalServiceTest" --no-daemon
```

전체 Easy 회귀 검증:

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '..\gradle-user-home')
.\gradlew.bat test --tests "*domain.easy*" --no-daemon
```
