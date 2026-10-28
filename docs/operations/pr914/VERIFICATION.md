# PR914 자동화 검증 결과 — 2026-10-28 KST

| 항목 | 결과 | 근거 |
|---|---|---|
| 신규 자동화 단위·실패 경로 검사 | PASS | 25개. 임시 JAR/미디어와 mocked systemctl/AWS 사용 |
| CloudFormation 스키마 | PASS | cfn-lint, 36KB inline template |
| Actions 표현식·job 의존성 | PASS | actionlint v1.7.7 |
| Python/Bash/JavaScript 구문 | PASS | py_compile / bash -n / node --check |
| 운영 브라우저 검사 등록 | PASS | Playwright --list: 1440·360·390·430px 4개 등록; 실제 페이지 검사 실행 아님 |
| Spring Boot JAR 교체 구조 | PASS (로컬) | 기존 로컬 JAR에 승인 UI 3개 적용, 나머지 모든 항목 해시 동일; java -Djarmode=tools ... list-layers 성공 |
| 기존 MediaAudit 파일 보존 | PASS (소스) | 아래 SHA-256으로 준비 파일 원문 일치 확인; AWS 원격 문서는 미조회 |
| AWS 스택 설치 / OIDC / 최소 권한 실행 | 미검증 | 현재 환경 AWS identity 없음 |
| 운영 정상 JAR/미디어 새 백업 | 미검증 | 운영 SSM 신규 실행 미실행 |
| 복구/재시작 운영 권한 | 미검증 | 로컬 mock 검사만 수행; 실제 복구 훈련 없음 |
| 운영 PC·모바일 실제 캡처 | 미검증 | 설치 후 Actions에서 생성하도록 구현 |
| PR914 병합·운영 배포 | 미실행 | 필수 운영 사전조건 미충족 |

MediaAudit 정확한 파일 SHA-256: `dcf762eb8ebea2c7332fd291ae7d9bc42241231b1bfb256ab87aaf9a1993ea77`.

이번 작업은 기존 350개 UI 검사 등을 반복한 결과가 아니다. 기존 PR914 head의 `build`, `mysql-integration-test`, `chromium-local` check-runs는 조회 시 success였으며 새 배포 실행에서는 이를 재조회한다.

초기 로컬 검사에서는 /var/tmp가 이 작업 환경에서 read-only여서 4개 임시 파일 테스트가 실패했다. 테스트에서 staging 경로를 임시 디렉터리로 분리한 뒤 25개 모두 통과했다. 운영 경로는 /var/tmp로 유지한다. AWS 권한이 있는 것처럼 테스트를 우회하지 않았다.
