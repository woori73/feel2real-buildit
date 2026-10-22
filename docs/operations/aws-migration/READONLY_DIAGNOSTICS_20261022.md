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
- `/etc/f2r/env`를 `source`하지 않는다. `DB_HOST/PORT/USER/NAME/PASSWORD` 다섯 키만 한 줄씩 읽고, 형식(호스트·포트·사용자·DB 이름 정규식)이 맞지 않으면 조회하지 않는다. 그래서 env 파일 안의 다른 비밀값(API 키·JWT 등)이 export되지 않고, 파일 내용이 명령으로 실행되지도 않는다.
- DB 비밀번호는 mysql 한 프로세스의 `MYSQL_PWD`로만 전달한다(인자·로그에 없음). `--no-defaults`, 연결 5초 제한, 모든 조회에 `SET SESSION TRANSACTION READ ONLY`.
- mysql 오류(사용자@호스트가 담김)는 버리고 `query_failed (detail suppressed)`만 출력한다. workflow도 `StandardErrorContent`를 출력하지 않는다.
- `app.jar.meta`는 `git_sha/jar_sha256/deployed_at` 키 줄만 출력한다.
- 출력은 계정 번호·RDS 호스트명·`*KEY/SECRET/TOKEN/PASSWORD=` 값을 가린다(저장소가 공개라 Actions 로그도 공개).

## 검증 (운영 접근 없음)

- `scripts/operations/test_readonly_diagnostics.py`(12건): 문서의 셸을 가짜 env(비밀번호·API 키·JWT 포함)와 가짜 mysql/systemctl/curl로 실행해 출력에 비밀값·RDS 호스트명·DB 사용자가 없는지, mysql에 다른 비밀값 변수가 전달되지 않는지, env 파일의 `$(...)`가 실행되지 않는지, 오류 상세가 숨겨지는지 확인. 정적 검사: 입력은 `Section`(allowedValues) 하나, 쓰기 SQL·재시작·`source` 없음, 정책 액션은 Describe/Get/List + 고정 문서 SendCommand뿐(와일드카드 액션·`AWS-RunShellScript` 없음), 신뢰는 main의 지정 workflow·승인 환경으로 고정, workflow `run`에 입력값 직접 삽입 없음.
- 보강 전 문서로 같은 시험을 돌리면 5건 실패(다른 비밀값 export, 오류 상세 노출, env 내용 실행, 메타 파일 전체 출력, `set -a`) → 보강이 실제 누출 경로를 막는다.
- `ops-readonly-check.yml`: 위 파일이 바뀌는 PR에서 actionlint 1.7.12·JSON 파싱·문서 셸 `bash -n`·시험을 실행한다(AWS 자격 증명 없음). `build-check`의 경로 필터로 기존 CI가 돌지 않던 문제를 메운다.

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
- RDS는 `f2r-db`, `f2r-db-202610181240`, `f2r-db-202610181331` 3개다. 기존 실행 기록으로 운영 DB는 **`f2r-db-202610181331`** 이다: 최종 전환 run [39131938382](https://github.com/woori73/feel2real-buildit/actions/runs/39131938382) `cut-new-db`(STAMP=202610181331)가 이 이름으로 복원하고 앱 env의 RDS 호스트를 바꾼 뒤 행 수 216표 일치·`app-healthy`, 이어 `cut-dns` 성공. `202610181240`은 run 39128226823의 실패한 전환(행 수 불일치 → `cut-rollback`)의 잔여, `f2r-db`는 10-18 오전 `db-migrate` 리허설 복원본이다. 이후 env의 DB 호스트를 바꾸는 단계는 확인 문자열이 필요한 `p2-cutover`뿐이고 다시 실행되지 않았다. 바이트 수준 최종 확인은 `Prod read-only status`의 `db_instance_identifier` 출력으로 한다. 나머지 2개는 비용 정리 대상이다(삭제는 소유자 판단, 이 PR은 아무것도 지우지 않는다).
- 스냅샷 대상 입력의 기본값은 비워 두고, 실행자가 `f2r-db-202610181331`을 직접 입력한다(정규식 `^f2r-db(-[0-9]{12})?$`).
- 활성화하려면 이 파일을 `.github/workflows/`로 옮기는 PR, 환경 생성, 역할 생성이 각각 승인되어야 한다.
