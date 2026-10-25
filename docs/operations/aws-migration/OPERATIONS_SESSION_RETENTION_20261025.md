# 운영 인증·증빙 보존 조사 (2026-10-25 KST)

## 판정

운영 접근 전체가 7일 만료된 것은 아니다. 20:19 KST에 기존 **Prod read-only status** 실행의 status job을 다시 실행하여 새 OIDC 역할 가정, EC2/RDS/ALB Describe, 고정 SSM 문서 실행이 모두 성공했다.

- 실행: [40177452268, attempt 2](https://github.com/woori73/feel2real-buildit/actions/runs/40177452268/attempts/2)
- 새 job: [121153838636](https://github.com/woori73/feel2real-buildit/actions/runs/40177452268/job/121153838636)
- 재실행은 원래 커밋 `745b5cf127f316120785c581b7c7a1b5a18d27a7`의 정의와 `section=jar-info`를 사용한다. 최신 운영 자원은 새로 읽지만, 이 문서의 변경된 워크플로를 검증한 실행은 아니다.
- Work 브라우저의 `native credential state cannot be safely resumed` 오류는 지속된다. 이 메시지를 AWS SSO 만료나 AWS 서비스 장애로 해석할 근거는 없다. 브라우저 세션 쿠키·토큰은 추출하지 않았다.

## ‘7일’ 설정 구분

| 항목 | 조사 당시 설정·근거 | 권장/이번 변경 |
|---|---|---|
| 지속 운영 워크플로 | deploy-ec2 / prod-readonly-status / prod-diagnostic은 수동 실행. YAML에 7일 만료 없음 | 수동 실행 유지. 자동 배포·새 승인 우회 경로 없음 |
| 일회성 IAM bootstrap | 저장소 신뢰 정책 `aws:CurrentTime < 2026-10-25T00:00:00Z` (KST 09:00). 실제 IAM 정책 일치 여부 미조회 | 만료 유지. 초기화 권한을 영구화하지 않음 |
| 운영 OIDC 역할 | 저장소 deploy / readonly / infra-builder 신뢰 정책에는 날짜 만료 없음. main·워크플로·aud·sub 제한 | 신뢰 정책/권한 변경 없음 |
| STS 자격증명 | configure-aws-credentials의 기본 3,600초. bootstrap의 역할 생성도 max-session-duration 3600 | 운영 배포·진단에 3600초 명시, 기존 runner 자격증명 제거 후 새 OIDC 발급 |
| 실제 IAM MaxSessionDuration | 기존 AWS 연결에는 iam:GetRole 권한 없음 | 미검증. 권한 확대 없이 유지 |
| AWS SSO·콘솔 세션 | 실제 Identity Center 설정·세션 만료시각 미조회. 운영 Actions는 SSO 대신 OIDC 사용 | 로그인 세션을 90일로 늘리지 않음. 장기 액세스 키 생성 없음 |
| 배포 검증 아티팩트 | deploy-gate-test-report 7일. run 40182828747의 artifact 12595201398은 11/01 만료 | 신규 배포 증빙 90일 |
| 진단 아티팩트 | prod-diagnostic의 평가 자료 7일 / 화면 자료 14일. readonly 현황은 별도 artifact 없음 | 각각 90일, readonly의 마스킹된 현황·실행 문맥도 90일 보존 |
| 별도 Artifact Storage Cleanup | 매주 예약 실행, 자동 삭제 ON, 보고서/빌드 파일 7일 기준 | 최소 90일, 예약 실행은 dry-run. 최근 성공 배포 3개 SHA·실행 중 SHA 보호 유지 |
| 저장소 전체 실행 로그 | 관리 설정 조회 API가 연결에서 지원되지 않음; 브라우저 접근 불가 | 실제 값 미확인. Actions General에서 90일 설정 필요. 새 readonly 실행은 GITHUB_RETENTION_DAYS를 기록·90 미만 경고 |
| S3 배포 파일 | app.jar / wiki / script를 고정 키로 덮어씀 | 승인된 다음 배포부터 `releases/<full SHA>/<run>-<attempt>/`에 별도 암호화 보관, 완료 manifest 마지막 등록 |
| S3 수명주기·버전 관리 | 실제 버킷 정책·lifecycle·versioning 미조회 | 변경·삭제하지 않음. releases/를 조기 만료시키는 규칙이 없는지 추가 확인 필요 |
| RDS 자동 백업 | 실제 운영 DB 7일 (20:19 KST Describe) | 그대로 유지 |
| 암호화 비밀 전송 artifact | migration-infra-build의 f2r-transfer-cipher는 1일 | 그대로 유지. 키·env·암호문을 일반 증빙과 함께 장기 보관하지 않음 |

GitHub의 공개 저장소 예약 실행은 60일 무활동 시 비활성화될 수 있지만, 이는 7일 artifact 만료와 별개다. 실제 workflow enabled/disabled 관리 상태 전체는 조회하지 못했다. 최신 성공 및 이번 재실행은 Actions와 운영 OIDC 접근의 사용 가능 근거다. 일회성 bootstrap 만료가 운영 역할의 만료를 뜻하지 않는다.

## 기존 실행 확인

| 실행 | 확인한 결과 | 한계 |
|---|---|---|
| migration infra 39099998277 (10/18 14:16 KST) | OIDC 성공, `STEP=all-infra` 성공. 해당 코드의 첫 단계는 step_validate(Describe/STS) | 개별 validate를 오늘 새로 실행한 것은 아님. 이 작업은 재실행하지 않음 |
| migration infra 39889566306 / 39889803177 (10/23 11:26 / 11:29 KST) | 실제 `STEP=app-diag`, OIDC·job 성공 | 당시 로그에는 WEMADE KIS websocket 경고 존재. 실행 성공을 모든 기능 정상으로 확대하지 않음 |
| Deploy to EC2 40182828747 (#1044, 10/25 19:04 KST 완료) | main bbe09a7b, 배포 job 성공 | 비차단 /wemade 후속 점검은 302로 경고. 재배포하지 않음 |
| Prod read-only status attempt 2 (10/25 20:19 KST) | 새 OIDC 인증, Describe·고정 SSM 모두 성공 | 원래 workflow revision 사용. 실기기·Studio 화면 검증 아님 |

## 최신 읽기 전용 진단값

- EC2 `i-0414de33d2803c723`, t3.large. ALB 8080·8082 모두 healthy.
- 운영 RDS `f2r-db-202610181331`: available / MySQL 8.4.8 / 암호화 / 삭제 보호 / 백업 7일. LatestRestorableTime **20:16:55 KST**.
- 실행 JAR SHA-256: `15b905706b3881ec1de814d46094b8f33ead8e41915a6bcffa7ba6bb231583c5`; #1044 빌드 기록과 일치.
- app.jar.previous SHA-256: `eb62e568b7ed24f4290fbbdf89456dce2a61a5b2a61cb47b73acdff90da119ab`; 배포 marker는 없음.
- 새 스냅샷·백업 생성, 실제 복구 실행, DB 변경, 서비스 재시작은 수행하지 않음.

## 변경의 적용 경계 및 검증

- PR #903(HEAD d86bce9a), PR #902, 애플리케이션·DB·Studio 자료 변경 없음. 별도 main 기반 운영 설정 변경이다.
- 신규 배포 보관 스크립트는 JAR·wiki 패키지·배포 스크립트 3개만 처리한다. env·API 키·업로드 자료를 수집하지 않는다.
- S3 AES256 + SHA-256 검증 + `If-None-Match: *`. 업로드 실패·checksum 불일치 시 완료 manifest/receipt를 만들지 않는다. 복구 payload 보관이 실패하면 라이브 키/파일 갱신 전에 배포를 중단한다.
- S3 장기 보관이 설정됐다고 실제 버킷 수명주기나 Object Lock이 검증된 것은 아니다. Object Lock·IAM·bucket lifecycle 변경은 하지 않았다. 아직 실제 배포 payload 신규 보관은 실행하지 않았다.
- 로컬 9개 테스트: 업로드 실패, checksum 오류, 덮어쓰기 거절, 잘못된 경로/입력, 90일 삭제 경계, dry-run, 배포 SHA 보호, 수동 실행/OIDC 계약. YAML 및 워크플로 내 Bash 문법 검사 통과.
- 기존 7일 만료 artifact와 이미 삭제된 로그의 보존기간은 소급 연장/복원되지 않는다. Build Check/Studio 전용 CI의 7일 artifact 설정은 PR #903을 건드리지 않기 위해 이번 범위에서 변경하지 않았다.

## 근거

- [GitHub 워크플로 활성화/비활성화](https://docs.github.com/actions/managing-workflow-runs/disabling-and-enabling-a-workflow)
- [GitHub 저장소 Actions·로그 보존 설정](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)
- [AWS STS 웹 ID 세션](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html)
- [S3 PutObject 조건부 쓰기·체크섬](https://docs.aws.amazon.com/cli/latest/reference/s3api/put-object.html)
