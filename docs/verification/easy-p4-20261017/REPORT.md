# EASY P4 — 수집→staging→승인→장부 반영 통합 검증 (2026-10-17)

> 범위: P1~P3를 재개발하지 않고 **격리 MySQL**에서 전체 흐름을 검증. 운영 DB·AWS·실제 금융기관 로그인·Muse Spark API 호출 없음.
> 환경: 이 세션 전용 MySQL **8.0.46**(포트 3411, 별도 데이터 디렉터리, 빈 스키마 `f2r_p4`). 운영은 8.4.8이므로 **8.4 동작은 미검증**(CI도 8.0 이미지). Flyway는 V1~V1089 전체를 빈 DB에 적용했다.

## 1. 결과 요약
| 구분 | main 그대로(`892e637c`) | 수정 후(이 PR) |
|---|---|---|
| 신규 통합 테스트 `EasyCollectionApprovalP4StagingTest` 9건 | **6건 실패**, 3건 통과 | **9건 통과** |
| `domain.easy.*` 전체 421건 | — | 418 통과 / 3 실패(아래 §4, 이 PR과 무관한 기존 실패) |
| 승인 관련 기존 테스트 24건 | 통과 | 통과 |

## 2. 발견한 결함
### D1 (치명) — 승인→장부 반영 API가 실제 MySQL에서 동작하지 않음
- 증상: `approveAndCommit`·`retryCommit`·`commitApproved` 호출 시 `JpaSystemException: Cannot execute statement in a READ ONLY transaction`. 장부 거래 0건, 승인 상태도 PENDING에 머묾.
- 원인: 서비스 클래스가 `@Transactional(readOnly = true)`인데 반영 경로가 같은 클래스의 `this.approve()`·`this.markCommittedTx()`를 호출한다. **자기 호출은 프록시를 거치지 않아 메서드별 `@Transactional`이 무시**되어 전체가 읽기 전용 트랜잭션 안에서 실행되고, 장부 INSERT와 `SELECT ... FOR UPDATE`가 모두 거부된다.
- 왜 못 잡았나: 기존 `EasyCollectionApprovalCommitMySqlTest`는 이름과 달리 엔티티 상태 전이만 시험하고 서비스를 호출하지 않는다(서비스·트랜잭션 경계 미검증).
- 영향: 승인 화면/API로 승인해도 장부에 반영되지 않는다. 데이터 손상은 없다(쓰기가 모두 거부됨).
- 수정(이 PR): 반영 경로를 `NOT_SUPPORTED`로 바꾸고 단계별 `TransactionTemplate` 트랜잭션으로 분리.
  1. 승인: 행 잠금 아래 PENDING→APPROVED (별도 쓰기 트랜잭션)
  2. 반영: 행 잠금 → 상태 재확인 → `importService.confirm()` → `markCommitted()` 를 **한 트랜잭션**으로 (장부와 상태가 원자적)
  3. 실패: 별도 트랜잭션에서 COMMIT_FAILED + 오류 기록(COMMITTED면 덮어쓰지 않음)

### D2 (설계 보강) — 동시 승인 경합
- 수정 전 설계: 승인(approve)에 행 잠금이 없어 두 요청이 모두 PENDING을 읽고 통과할 수 있고, 장부 반영도 서로 다른 트랜잭션이라 중복 거래 가능성이 있었다(D1 때문에 실제로는 관찰되지 않음).
- 수정: 승인·반영 모두 행 잠금. 결과: 동시 두 요청 중 **하나만 COMMITTED, 다른 하나는 `IllegalStateException`(이미 처리됨)**으로 거부, 장부는 3건 그대로.

## 3. 시나리오별 검증 결과 (수정 후, 격리 DB)
| 시나리오 | 결과 |
|---|---|
| V1088·V1089 적용·스키마 | `flyway_schema_history` 1088·1089 성공, 컬럼 `committed_at`·`commit_error`, FK 2개 확인 |
| CSV 수집→staging | 3행 업로드 → import batch 생성, 행 3건 staging, **장부 0건** |
| 중복 제거 | 같은 CSV 재업로드: 새로 받아들인 행 0, duplicateCount 2 |
| 승인 전 장부 미반영 | 승인 요청(PENDING) 후 장부 거래 0건 |
| 승인 후 1회 반영 | 2행 → 장부 2건, 상태 COMMITTED가 DB에 저장됨(committed_at 기록) |
| 재호출 | COMMITTED 건 `retryCommit` → 장부 건수 불변 |
| 실패 후 재시도 | `confirm` 일시 실패 주입 → COMMIT_FAILED, 장부 0건 → `retryCommit` 후 장부 2건, COMMITTED |
| **commit 성공 직후 실패 주입** | 실제 `confirm`이 끝난 직후 예외 → 같은 트랜잭션이라 **장부 INSERT도 롤백(0건)**, 상태 COMMIT_FAILED → 재시도 후 **정확히 2건**(중복 0) |
| 동시 승인 | 위 D2 결과 |
| 테넌트 격리 | 소유자 B의 A 사업체 요청·승인 → `AccessDeniedException`; B 경로에 A의 승인 ID → `SecurityException`; A·B 장부 0건, 승인 상태 PENDING 유지 |

## 4. 이 PR과 무관한 기존 문제(참고, 수정 안 함)
- `DashboardWidgetControllerTest`(#740, Muse) 3건 실패: 단독 실행에서도 재현(`NullPointerException`·`UnfinishedStubbingException`). CI 허용 목록에 없어 CI에서는 드러나지 않았다. 별도 확인 필요.

## 5. 미검증·한계
- **PC·모바일 승인 화면: 확인하지 못함.** `/api/admin/easy/collection-approvals`를 호출하는 화면(템플릿·JS)이 저장소에 없다(검색 결과 0건). 기존 승인 UI는 카드 이벤트 검토(`/easy/card-review`)와 에이전트의 제안 카드뿐이며 이 승인 테이블과 연결되지 않는다. 캡처할 화면이 없어 제출하지 않는다 → **승인 화면은 별도 구현 항목.**
- HTTP 계층(컨트롤러 응답 코드·직렬화) 시험 없음: 서비스 계층까지만 검증. 컨트롤러가 `IllegalStateException`을 어떤 상태 코드로 변환하는지는 미확인.
- 실제 Muse Spark API 호출: 미검증 항목으로 유지(사용하지 않음).
- MySQL 8.4.8 대상 시험, 대용량(수천 행) 시험, 반영 중 프로세스 강제 종료(크래시) 시험은 하지 않았다.
- 테넌트 격리 시험은 접근 서비스를 모의(mock)로 대체하고 서비스 계층의 사업체 불일치 검사를 확인한 것이다(실제 학원-사업체 연결 로직은 별도).

## 6. 재현
격리 MySQL 8.0에 빈 스키마를 만들고 `SPRING_DATASOURCE_*`로 연결한 뒤 `./gradlew test --tests '*EasyCollectionApprovalP4StagingTest'`. 이 테스트는 CI `mysql-integration-test` 목록에 추가했다.
