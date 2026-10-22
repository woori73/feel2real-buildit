# 신규 계정 읽기 전용 진단 체계 (2026-10-22)

## 배경

- 계정 이전 후 운영은 신규 계정 `425805163960`의 `f2r-app` 인스턴스(태그 `Name=f2r-app`, `Project=f2r-migration`)다.
- `prod-diagnostic`을 포함한 여러 진단 workflow가 구 계정 역할과 구 인스턴스 `i-09d7cabb5a42d1135`를 고정해 두었다. 2026-10-22 실행에서 SSM은 `InvalidInstanceId`로 거부되었고, 구 역할은 RDS·IAM·S3 조회가 `AccessDenied`였다.
- 신규 계정 배포 역할 `github-actions-f2r-deploy`의 신뢰 정책은 `deploy-ec2.yml`·`migration-infra-build.yml`(main)만 허용하므로, 새 진단 workflow는 그 역할을 쓸 수 없다.

## 이 PR이 바꾸는 것

| 파일 | 내용 |
|---|---|
| `.github/workflows/prod-readonly-status.yml` | 신규. EC2·RDS(버전·백업 보존·최근 복원 가능 시점·스냅샷)·ALB 대상 상태 Describe와 고정 SSM 문서 실행 |
| `.github/workflows/prod-diagnostic.yml` | 구 역할·구 인스턴스 하드코딩 제거. 태그로 인스턴스를 찾고 같은 고정 문서의 Section만 실행. 구 호스트 전용 진단과 쓰기 작업(`deploy-wemade-jar`, `rollback-jar` 등)은 선택지에서 제거 |
| `trust-github-actions-f2r-readonly.json` | main 브랜치의 위 두 workflow만 역할을 가정할 수 있음 |
| `policy-f2r-readonly-status.json` | Describe 계열, 고정 문서 `F2R-ReadOnlyStatus`에 대한 `ssm:SendCommand`(태그 인스턴스 한정), 결과 조회만. `AWS-RunShellScript` 불가, 쓰기·스냅샷·IAM 권한 없음 |
| `ssm-document-F2R-ReadOnlyStatus.json` | 고정 셸(서비스 상태, jar 크기·시각·SHA-256, health, 메모리·디스크, Flyway `SELECT`(READ ONLY 세션), 플래그 이름만). `Section`은 `allowedValues`로 제한 |

## 최소 권한 근거

- 호스트 명령은 문서에 고정되어 있어 역할을 탈취해도 임의 셸을 실행할 수 없다(`AWS-RunShellScript` 미허용).
- DB 비밀번호는 `MYSQL_PWD` 환경변수로만 전달해 프로세스 목록·로그에 남지 않는다. DB 세션은 `SET SESSION TRANSACTION READ ONLY`.
- 출력은 계정 번호·RDS 호스트명·`*KEY/SECRET/TOKEN/PASSWORD=` 값을 가린다(저장소가 공개라 Actions 로그도 공개).

## 적용 절차 (승인 후, 소유자가 콘솔/관리 역할로)

1. SSM 문서 생성: 이름 `F2R-ReadOnlyStatus`, 유형 Command, 내용 = `ssm-document-F2R-ReadOnlyStatus.json`.
2. IAM 역할 `github-actions-f2r-readonly` 생성: 신뢰 정책 = `trust-github-actions-f2r-readonly.json`, 인라인 정책 = `policy-f2r-readonly-status.json`.
3. 이 PR 병합 후 `Prod read-only status`를 `section=all`로 실행.
4. 결과로 확인: RDS 엔진·버전, 자동 백업 보존 일수·최근 복원 가능 시점, 수동 스냅샷 목록, Flyway 최대 버전과 V1094/V1095 적용 여부, jar SHA-256·수정 시각.

## 남은 정리 (이번 범위 아님)

구 계정 역할 또는 구 인스턴스를 고정한 workflow가 더 있다: `artcraft-setup`, `diagnose-*`(6개), `easy-ledger-census*`, `easy-provenance-check`, `ec2-diagnose`, `newstart-prod-census-readonly`, `newstart-seed`, `old-account-probe`, `old-host-inventory`, `staging-ec2`. `deploy-ec2`와 `migration-infra-build`는 `old` 대상을 긴급 복구용으로 의도적으로 유지한다. 나머지는 사용 여부를 확인한 뒤 신규 계정으로 옮기거나 삭제한다.

## 배포 직전 RDS 스냅샷 (초안, 비활성)

- `rds-predeploy-snapshot.yml`(이 폴더, `.github/workflows` 밖): 승인 환경 `f2r-production-snapshot` + 확인 문자열 `SNAPSHOT-<sha8>` + 스냅샷 1개 생성·대기만. 삭제·복원 없음.
- 전용 역할 `github-actions-f2r-snapshot`: 신뢰는 main의 이 workflow·환경만(`trust-github-actions-f2r-snapshot.json`), 권한은 `f2r-db*` 인스턴스에서 `f2r-predeploy-*` 이름의 스냅샷 생성과 Describe만(`policy-f2r-predeploy-snapshot.json`). 배포·읽기 전용 역할과 분리.
- 2026-10-22 `validate` 조회에서 RDS가 `f2r-db`, `f2r-db-202610181240`, `f2r-db-202610181331` 3개다. 어느 것이 운영인지 확정하기 전에는 스냅샷을 찍지 않는다 — `Prod read-only status`의 `db_instance_identifier`로 확인한다. 나머지 2개가 이전 리허설 잔여물이면 비용 정리 대상이다(삭제는 소유자 판단).
- 활성화하려면 이 파일을 `.github/workflows/`로 옮기는 PR, 환경 생성, 역할 생성이 각각 승인되어야 한다.
