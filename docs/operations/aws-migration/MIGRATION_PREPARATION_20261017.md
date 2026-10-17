# 신규 AWS(425805163960) 이전 사전 준비 (2026-10-17)

> **이 문서는 준비·검토만 한다. AWS 리소스 생성·변경, IAM 역할 생성, 운영 DB 변경, DNS 변경, 운영 배포는 모두 미실행이며 각각 별도 승인 대상이다.**
> 선행 문서: `AWS_INFRA_REVIEW_20261017.md`(#741), `AWS_ACCESS_AND_PROTECTION_20261017.md`(#742). 이 세션은 AWS 자격 증명이 없어 AWS를 직접 조회·검증하지 못했다. 비용은 기억 기반 추정치다.

## 1. 신뢰 정책·최소 권한 정책 최종 검토 (작업 2)
검토 대상: `trust-f2r-infra-builder.json`, `policy-f2r-infra-builder.json`(6,107자, 인라인 한도 10,240자), `trust-github-actions-f2r-deploy.json`, `policy-f2r-deploy-smoke-ssm.json`.

| 항목 | 결과 |
|---|---|
| 신뢰 정책 `sub` | `repo:woori73@299170593/feel2real-buildit@1340651018:environment:f2r-infra-build` — **2026-10-17 실측(run 39034460705 계열)과 문자열 일치 확인됨**. 불변 ID 형식 유지 |
| `aud` / `job_workflow_ref` | `sts.amazonaws.com` / `...migration-infra-build.yml@refs/heads/main`. 한 `StringEquals` 블록(AND). 조건 키 지원은 사용자가 AWS 지원으로 확인함 |
| 우회 경로 | 비-main·포크 PR: environment 배포 브랜치 제한(main)과 `job_workflow_ref`로 이중 차단. environment 줄 삭제: `sub` 불일치로 가정 거부. 남는 위험: **main에 악성 변경을 병합** → 병합 경로(PR+CI)와 Environment 승인으로 통제, 사용자 승인 UI가 마지막 방어선 |
| 권한 정책 대조 | 워크플로가 호출하는 AWS API 51개를 자동 추출해 정책과 대조: **실제 누락 0**(`Db`/`DB` 표기 차이와 `wait`은 Describe/GetCommandInvocation으로 허용됨) |
| 삭제·종료·복원·DB 생성·S3 | 없음(의도). 실패 시 정리는 사용자가 콘솔에서 한다(§7) |
| 배포 역할 신뢰 정책 | `sub`=main ref, `job_workflow_ref`=`deploy-ec2.yml`·`migration-infra-build.yml`. **smoke 완료 후 후자 제거 필요**(별도 변경, 승인) |

**이번 검토에서 새로 확인한 한계·권고 (정책 파일은 바꾸지 않았다)**
1. `iam:CreateRole`은 이름(`f2r-ec2-ssm-role`)만 제한하고 **신뢰 정책(누가 이 역할을 가정하나)은 제한하지 않는다.** 붙일 수 있는 정책은 `AmazonSSMManagedInstanceCore` 1종이고 PassRole은 EC2뿐이라 영향은 작다. 더 줄이려면 사용자가 EC2 역할·인스턴스 프로파일 2개를 콘솔에서 직접 만들고(절차 §2 A-3) 빌더 정책에서 `CreateRole/CreateInstanceProfile/AttachRolePolicy/AddRoleToInstanceProfile` 4개 문장을 제거한다. **권고: 사용자가 직접 생성(빌더에서 IAM 쓰기 제거).** 선택은 사용자 몫이다.
2. EC2 인스턴스의 S3 배포 버킷 읽기 권한(배포 시 필요)은 이 정책에 없다 — 컷오버 단계(승인 E 이전 별도 승인)에서 EC2 역할에 추가한다.
3. ACM DNS 검증 CNAME을 가비아에 추가하는 일은 **서비스에 영향 없는 DNS 변경이지만 DNS 변경이다.** 인프라 구축(C) 승인과 별개로 그 시점에 사용자가 직접 하거나 별도 승인한다.
4. IAM Access Analyzer `validate-policy`와 실제 `RunInstances` 평가는 하지 못했다. 역할 생성 후 `validate`→smoke 순서로만 확인 가능하다(§4).
5. RDS 인스턴스 생성·복원은 이 정책에 없다(별도 역할/콘솔, 승인 E).

## 2. 사용자 직접 수행 IAM 설정 절차 — 신규 계정 425805163960 (작업 3)
**승인 A를 받은 뒤에만** 사용자가 콘솔(MFA)에서 수행한다. Claude는 AWS를 변경하지 않는다. 장기 액세스 키는 만들지 않는다.

0. (A 전 권장 가드레일) 루트 MFA 확인 · CloudTrail 관리 이벤트 켬 · Budgets 50/80/100% 예측 알림 + Cost Anomaly Detection.
1. **A-1 OIDC 공급자**: IAM → 자격 증명 공급자 → 추가 → OpenID Connect, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`. (썸프린트 입력 불필요)
2. **A-2 `f2r-infra-builder` 역할**: IAM → 역할 → 생성 → 사용자 지정 신뢰 정책 → `trust-f2r-infra-builder.json` 내용 붙여넣기 → 권한 없이 생성 → 인라인 정책 `f2r-infra-builder-inline` 추가(`policy-f2r-infra-builder.json` 내용) → 최대 세션 1시간 → 태그 `Project=f2r-migration`. 역할 ARN이 `arn:aws:iam::425805163960:role/f2r-infra-builder`인지 확인(워크플로 고정값).
3. **A-3 (권고 §1-1)** EC2용 역할 `f2r-ec2-ssm-role`(신뢰: `ec2.amazonaws.com`, 정책 `AmazonSSMManagedInstanceCore`)과 인스턴스 프로파일 `f2r-ec2-profile`을 직접 생성하고 빌더 정책에서 IAM 쓰기 4개 문장 제거. 직접 만들지 않는 경우 이 단계는 건너뛴다(빌더가 만든다).
4. **A-4 `github-actions-f2r-deploy` 역할(스모크·배포용)**: 신뢰 `trust-github-actions-f2r-deploy.json`, 권한 `policy-f2r-deploy-smoke-ssm.json`. build 단계(vpc~acm)에는 불필요하므로 **smoke 직전에 만들어도 된다.**
5. 생성한 역할의 신뢰 정책·권한이 파일과 같은지 콘솔 JSON 탭에서 대조 후 "생성 완료"만 알려주면 된다(JSON 원문을 채팅에 붙이지 않아도 된다).
6. 변경 금지: 구 계정(067573916809)의 기존 OIDC 신뢰 정책.

## 3. 사용 전 확인할 GitHub 쪽 상태
- Environment `f2r-infra-build`: reviewers=woori73, 배포 브랜치 main — **검증 완료**(비-main 차단, main 승인 대기 후 실행; #742 §3-2).
- 저장소 OIDC 클레임은 불변 ID 형식으로 이미 나온다(변경하지 않는다).

## 4. Environment `sub` ↔ 신뢰 정책 일치 재검증 방법 (작업 4)
| 단계 | 방법 | AWS 필요 | 승인 |
|---|---|---|---|
| ① 문자열 일치 | `oidc-claims-env.yml`(docs 사본, 환경 `f2r-infra-build`)로 출력한 `sub`를 `trust-f2r-infra-builder.json`의 `sub`와 바이트 비교 | 아니오 | 불필요(이미 1회 일치). 역할 생성 직전 1회 더 실행 권장 |
| ② 긍정 검증 | 승인 B 후 `migration-infra-build.yml` `step=validate`(Environment 승인 필요): 역할 가정 후 `sts get-caller-identity`만 수행 | 예(가정만) | B 병합 + C-0(validate 한정) |
| ③ 부정 검증 | `assume-role-negative-check.yml`(이 디렉터리, 비활성): 비-main 브랜치·environment 없이 가정 시도 → **반드시 거부**. 성공하면 신뢰 정책이 열린 것이므로 즉시 중단·보고 | 예(가정 시도) | A 직후 1회, 단일 브랜치 `ci/assume-negative`(검증 후 삭제는 별도 승인) |
| ④ 실패 시 | 원인(`sub`/`job_workflow_ref`) 분리: 로그의 클레임과 정책을 비교. **신뢰 정책을 임의로 완화하지 않는다**(fallback은 `trust-...fallback-branch.json`이며 별도 승인) | | |

## 5. 구 계정 읽기 전용 조사 역할 — 정책 검토만 (작업 5, 생성하지 않음)
대상: `policy-old-account-migration-discovery.json`(40액션), `trust-old-account-migration-discovery.json`, `migration-discovery-readonly.yml`.
- 신뢰 정책: environment `f2r-aws-discovery` + `job_workflow_ref` main, 불변 ID `sub`. 구조는 신규 빌더와 동일하고 적절하다.
- **발견 1 — 공개 저장소 로그 노출(중요):** 이 저장소는 공개라 Actions 로그와 step summary도 공개다. 조사 워크플로는 계정 ID·IAM 사용자명·액세스 키 ID·버킷명·KMS 키 ID·호스트 영역·비용을 **그대로 로그에 출력**한다. 비밀 값은 없지만 계정 정찰 정보를 공개한다. → **권고: 조사는 CI가 아니라 사용자 CloudShell에서 읽기 전용 스크립트(`discovery-readonly-cloudshell.sh`)로 수행**한다. 이 경로는 **구 계정에 역할을 새로 만들 필요가 없어 승인 D 자체가 불필요**해진다. 사용자는 필요한 항목만 요약해 전달한다.
- **발견 2 — `ec2:Describe*`는 `DescribeInstanceAttribute`(userData)를 포함**해 EC2 user-data에 비밀이 있으면 읽힌다. CI 방식을 유지한다면 `Describe*`를 명시 목록으로 바꾸거나 `ec2:DescribeInstanceAttribute` 명시 Deny를 추가해야 한다(정책 수정은 승인 D에 포함).
- 발견 3: 배포 역할 신뢰 정책 조사에 필요한 `iam:GetRole`·`ListRolePolicies`·`GetRolePolicy`·`ListAttachedRolePolicies`는 있으나 **`cloudtrail:LookupEvents`가 없어** 어떤 브랜치가 역할을 가정했는지 90일 이력 조회가 안 된다(CloudShell 스크립트에는 포함, 읽기 전용). `s3:ListBucket`은 객체 키 이름을 노출하므로 필요 없으면 제거 권고. `ce:GetCostAndUsage`는 호출당 소액 과금.
- 스크립트 검증: `bash -n` 통과, 쓰기 계열 AWS 호출 0건(정규식 점검). 실제 실행은 하지 않았다.

## 6. 구 운영 배포용 OIDC 신뢰 정책 과도 권한 조사 방법 (작업 6, 구 계정 변경 없음)
근거: 2026-10-16 01:08 UTC 비-main 브랜치(`ci/rds-snapshot-predeploy`)가 이 역할을 가정에 성공(run 38833505889) → 신뢰 정책이 main으로 고정되지 않은 것으로 보인다(원문 미확인).
1. **원문 확인(읽기):** `iam get-role --role-name github-actions-f2r-deploy` → `AssumeRolePolicyDocument`. `sub`에 `*`/`repo:woori73/*` 패턴이 있는지, `job_workflow_ref` 조건이 없는지 본다. (CloudShell 스크립트 포함)
2. **실제 가정 이력(읽기):** CloudTrail `AssumeRoleWithWebIdentity` 90일 — 호출자 `sub` 값별 횟수. 비-main·environment 외 값이 있으면 과도 개방의 실증. (스크립트 포함. `userIdentity.userName`에 `sub`가 기록된다는 점은 이 세션에서 확인하지 못함)
3. **권한 범위(읽기):** 붙은 관리형·인라인 정책과 `iam:GenerateServiceLastAccessedDetails`로 실사용 서비스 확인 → 필요 최소 권한 도출(SSM SendCommand 대상 인스턴스, S3 배포 버킷만).
4. **GitHub 쪽(읽기):** 이 역할을 쓰는 워크플로 파일(`deploy-ec2.yml`, 진단·`ci/*`)과 비-main 실행 이력 목록.
5. **노출 평가:** push 권한 보유자가 임의 브랜치에서 운영 EC2에 SSM 명령을 보낼 수 있는지(=`sub` 와일드카드 + `ssm:SendCommand` 범위)를 1~3으로 확정.
6. **수정안(승인 후에만):** `sub=repo:woori73@299170593/feel2real-buildit@1340651018:ref:refs/heads/main` + `job_workflow_ref`(배포 워크플로 목록)로 좁힘. 적용 전 롤백용으로 현재 신뢰 정책 원문 저장, 적용 직후 정상 배포 경로(main) 가정 확인. **승인 전에는 변경하지 않는다.**

## 7. 비용 비교와 실패 시 복구 (작업 7) — 월 비용은 기억 기반 추정, AWS 요금 계산기로 재확인 필요
| 시나리오 | 월 비용 | 비고 |
|---|---|---|
| S0 현재(구 계정만) | **미확인(X)** | 구 계정 Cost Explorer를 CloudShell 스크립트로 확인 필요 |
| S1 신규 구축 직후(RDS 복원 전, 컷오버 전 대기) | 약 $55~59 + 송신 데이터 | EC2 t3.small ~$20(§10에서 t3.medium으로 변경), EBS 20GB ~$1.8, 공인 IPv4 3개 ~$11, ALB ~$16+LCU $3~6, S3 $1~2 |
| S2 RDS 복원 후 신규 단독 | 약 $77~85 + 송신 데이터 | db.t3.micro 기준(구 RDS 크기 확인 후 재산정) |
| S3 구·신 동시 운영(컷오버 후 롤백 대기) | X + 약 $80 | 대기 기간(권고 7일 이상)만큼 이중 |
- 크레딧 $100 기준 S2는 약 1.2~1.3개월. 단일 EC2·단일 AZ(가용성 낮음)임을 수용해야 한다.
- **구 EC2 유지가 더 싸다**면(X < $80): 이전 사유는 비용이 아니라 계정 소유권·크레딧이다. 크레딧 소진 후 비용 비교를 사용자가 판단한다.

**실패 시 복구**
| 단계 | 실패 시 | 복구 |
|---|---|---|
| C(인프라 구축) 중간 실패 | 재실행 안전(리소스별 존재 확인·태그 기반) | 재실행. 부분 리소스는 과금되므로 정리가 필요하면 **사용자가 콘솔에서 Project=f2r-migration 태그 기준 삭제**(빌더에는 삭제 권한 없음). 삭제 순서: ALB 리스너·ALB·대상 그룹 → EC2 → SG → 서브넷·라우트·IGW → VPC. ALB는 삭제 방지가 켜져 있어 먼저 해제 |
| validate/smoke 실패 | 가정 거부·SSM 미등록 | 신뢰 정책을 완화하지 않고 원인 분리(§4④). SSM 에이전트·프로파일·IMDS 확인 |
| 컷오버(E) 실패 | 신 환경 오류 | 구 환경을 컷오버 후에도 유지하므로 **DNS를 구 환경으로 되돌림**. 사전에 TTL을 낮추고(예: 60~300초), 이전 레코드 값을 기록 |
| 데이터 | 신 환경에서 쓰기가 발생한 뒤 롤백하면 신규 데이터 유실 | 컷오버 시점에 쓰기 동결(점검 모드) → 최종 스냅샷 복원 → 전환. 되돌릴 때는 신 환경 쓰기분을 별도 덤프로 보존 후 판단 |

## 8. 운영 DB 스냅샷으로 V1085→V1088 Flyway 검증 계획 (작업 8, 미실행)
**현재 사실:** 운영은 V1085. 새 마이그레이션 3개는 모두 **신규 테이블 생성만**(`IF NOT EXISTS`): V1086 `academy_inquiries`, V1087 `site_events`, V1088 `easy_collection_approvals`(FK → 이미 있는 `easy_collection_runs`(V983)·`easy_businesses`(V973) 참조 — 순서 문제 없음). 기존 테이블 변경·데이터 이동 없음. V1086→V1087은 로컬 DB에서 검증됨, **V1088 포함 전체는 운영 데이터로 시험한 적 없음.**

| # | 절차 | 확인 | 승인 |
|---|---|---|---|
| 1 | 구 RDS 엔진 버전·암호화·KMS 확인(CloudShell 스크립트) | 스냅샷 공유 가능 여부(기본 AWS 관리형 키는 공유 불가 → 고객 관리형 키로 사본 필요) | 불필요(읽기) |
| 2 | 스냅샷 생성(수동 스냅샷, 운영 영향 거의 없음) | 스냅샷 `available` | **승인 E-1** |
| 3 | 신규 계정으로 공유(+KMS 권한) 후 신규 계정에서 **임시 인스턴스로 복원**(운영과 분리, 공인 접근 없음, 같은 파라미터 패밀리) | 복원 성공·엔진 버전 일치 | **승인 E-1**(RDS 생성 권한은 빌더에 없으므로 사용자 콘솔 또는 별도 정책) |
| 4 | 복원본 사전 측정: `flyway_schema_history` 최종 버전(=1085), 테이블별 행 수·체크섬 샘플, 새 3개 테이블 부재 확인 | 기준값 기록 | E-1 |
| 5 | 배포 후보 jar(main HEAD)로 **복원본에만** `flyway migrate`(앱을 복원본에 부팅 또는 Flyway CLI) | V1086→V1088 3건 성공, `validate` 통과, 소요 시간 | E-1 |
| 6 | 사후 확인: 기존 테이블 행 수·샘플 체크섬 동일, 새 테이블 0행, FK 생성 확인, 앱 `/actuator/health` UP, 주요 화면 스모크 | 불일치 0 | E-1 |
| 7 | 재실행(멱등) 1회: 두 번째 migrate가 no-op | 변경 없음 | E-1 |
| 8 | 롤백 시험: 새 3개 테이블 DROP(복원본에서) → 이전 jar(V1085)로 부팅 시 문제 없음(추가 테이블은 무시됨) 확인 | 구 jar 호환 | E-1 |
| 9 | 임시 인스턴스 삭제(사용자 콘솔), 스냅샷 보존 정책 결정 | 과금 정리 | E-1 |
- **운영 DB에는 어떤 변경도 하지 않는다.** 운영 적용은 컷오버 승인(E-2)에서 별도로 받는다.
- 예상 비용(복원 시험): db.t3.micro 수 시간~1일 소액(수 달러 이하, 추정).
- 위험: ①암호화 스냅샷 공유 불가(KMS) ②파라미터 그룹 패밀리 불일치(8.0↔8.4) ③복원 소요 시간(용량 비례) ④개인정보 포함 데이터의 계정 간 이동 → 신규 계정 접근 최소화·시험 후 삭제.
- 이전 PR에서 다른 에이전트(Muse)가 V1088을 추가했음 — 이번 검증으로 같은 목록(V1086~V1088)을 고정하고, 이후 새 V 파일이 main에 들어오면 계획을 갱신한다.

## 9. 승인 요청 (각각 독립)
| 코드 | 내용 | 변경 대상 | 사전 확인 | 복구 |
|---|---|---|---|---|
| **A** | 신규 계정 OIDC 공급자·IAM 역할 생성(사용자 콘솔, §2) | 신규 계정 IAM만 | 신뢰 정책 JSON 대조, 부정 검증(§4③) 준비 | 역할·공급자 삭제(사용자) |
| **B** | `migration-infra-build.yml`을 `.github/workflows/`로 승격하는 PR 병합(Muse 작성, Claude 검토) | GitHub main(워크플로 파일) | 정적 검증·정책 대조·CI | PR revert |
| **C** | 실제 AWS 인프라 구축 실행(단계별 Environment 승인; C-0 `validate` → vpc → ec2 → alb → acm → alb-https → smoke) | 신규 계정 VPC/EC2/ALB/ACM(RDS 제외) | A·B 완료, 예산 알림, 월 약 $55~59 | 사용자 콘솔 삭제(§7) |
| **D** | 구 계정 조사 역할 생성 | 구 계정 IAM | **권고: CloudShell 스크립트로 대체 → D 불필요**. 역할이 필요하면 §5 발견 2·3 반영 후 | 역할 삭제 |
| **E** | 운영 데이터 이전·DNS 전환: E-1(스냅샷·복원 시험), E-2(운영 DB 적용·최종 이전), E-3(DNS 전환), E-4(구 환경 종료) | 구/신 계정 RDS·S3, 가비아 DNS | §7·§8, TTL 낮춤, 쓰기 동결 | DNS 되돌림, 구 환경 유지 |
- 운영 배포·운영 DB 변경·DNS 변경은 E 안의 하위 승인으로 각각 따로 받는다.
- ADMIN 실제 ID/비밀번호 변경은 이 승인들과 무관하다(배포 후 별도 승인, 사용자가 `/admin/account`에서 직접 입력).

## 10. EC2 4GB(t3.medium) 확정 반영 — 최종 구축 계획·비용 (2026-10-17 사용자 확정, 미실행)
**반영한 변경(파일):** `policy-f2r-infra-builder.json`의 `ec2:InstanceType` 조건 `t3.small`→`t3.medium`, `migration-infra-build.yml`의 `--instance-type t3.small`→`t3.medium`. 둘이 어긋나면 `RunInstances`가 AccessDenied가 되므로 같이 바꿨다(JSON·YAML 파싱 확인). 정책 크기 변동 없음. 실제 생성은 하지 않았다.

**구성 확정안(승인 C의 대상):** t3.medium(2 vCPU·4GB) 1대, AL2023 x86_64, IMDSv2 필수·hop 1, 루트 gp3 20GB 암호화(권고: 로그·jar 이력·임시 덤프를 고려해 **30GB**로 변경 시 월 약 +$0.8 — 사용자 판단), 공개 서브넷(공인 IP), SSH·키페어 없음(SSM만), 인바운드는 ALB SG의 8080만. ALB 2AZ·idle 180초·80→443·삭제 방지, ACM 와일드카드, RDS는 §10-3. 인스턴스 크기는 이후 변경하려면 정책 조건도 바꿔야 한다(중지 후 변경 + 정책 수정 승인).

**비용 재산정(Seoul 온디맨드, 기억 기반 추정 — 요금 계산기로 재확인 필요):** t3.medium은 t3.small 대비 약 +$19(월 약 $38~40).
| 시나리오 | 월 비용 | 직전 안(t3.small) |
|---|---|---|
| S1 신규 구축 직후(RDS 전) | 약 $73~78 + 송신 | $55~59 |
| S2 RDS 복원 후(RDS는 §10-3 산정 전, db.t3.micro 가정) | 약 $95~103 + 송신 | $77~85 |
| S3 구·신 동시 운영 | X(구 계정, 미확인) + S2 | |
- **크레딧 $100은 S2 기준 약 1개월**로 줄어든다. 24시간 이전 후 구 환경을 롤백 대기로 병행하면 이중 비용이 즉시 발생한다. Budgets는 알림일 뿐 정지하지 않으므로 50/80/100% 예측 알림 + Cost Anomaly Detection을 **구축(C) 전에** 켜는 것을 사전 조건으로 한다.
- 절감 옵션(결정은 사용자): 약정(1년 Savings Plan 등)은 11월 컨테이너 이전 계획이 있어 **지금은 권하지 않음**. 컨테이너 이전 시 EC2 크기·ALB 구성이 바뀔 수 있다.

**§10-3 RDS 산정(별도, 미확정):** 구 RDS의 엔진 버전·클래스·할당 용량·실사용(CPU·연결 수·FreeableMemory·IOPS)·암호화/KMS를 CloudShell 스크립트(`discovery-readonly-cloudshell.sh`)로 확인한 뒤 결정한다. CloudWatch 지표 조회(`cloudwatch:GetMetricStatistics`)는 스크립트에 아직 없다 — **조사 전에 읽기 전용 호출 3개(FreeableMemory·CPUUtilization·DatabaseConnections, 14일 최대·평균)를 추가**한다. 이 값 없이 크기를 정하지 않는다. 4GB EC2 + RDS로 JVM 힙은 컨테이너 이전 전까지 `-Xmx` 약 2~2.5GB 범위로 시작하고 배포 후 메모리를 관찰한다(구 설정 확인 필요, 미확인).

**24시간 이전 목표의 현실성(위험·선행 조건):**
| 단계(소요 추정) | 병목·위험 |
|---|---|
| A 승인·역할 생성(수십 분) | 사용자 콘솔 작업 |
| B 워크플로 승격 PR·CI(약 1시간) | |
| C 구축(약 1~2시간) | **ACM 검증 CNAME(가비아 DNS 변경) 필요**, 발급 대기. ALB 443은 발급 후 |
| 구 RDS 확인 + 스냅샷(수 시간) | 암호화 스냅샷 공유(KMS) 가능 여부에 따라 **사본 생성이 추가**됨 |
| 복원·Flyway 검증(§8)·스모크(수 시간) | 데이터 크기 비례 |
| 최종 쓰기 동결 → 최종 스냅샷·복원 → DNS 전환(1~2시간) | **TTL을 사전에 낮춰야 하며(최소 이전 TTL만큼 선행)** DNS 전환·롤백이 가능한 창에서 진행 |
- 24시간은 **병렬 진행하고 승인이 지연되지 않는다는 전제**에서 가능하다. 가장 긴 직렬 구간은 스냅샷 공유/복원이며 구 RDS 크기 확인 전에는 확정할 수 없다. 시간을 맞추려고 §8 검증(복원본 Flyway 시험)이나 롤백 준비(DNS TTL, 구 환경 유지)를 생략하지 않는다.

**운영 DB 백업·복구 계획 유지(변경 없음):** 구 RDS는 이전 완료 후에도 **최소 7일 유지**(삭제·중지는 E-4 별도 승인). 컷오버 직전 수동 스냅샷 1회(보존, 삭제 금지), 기존 자동 백업 보존 기간은 줄이지 않는다. 신 RDS는 복원 직후 자동 백업 켬(보존 ≥7일)·삭제 방지 켬. 복구 기준: 컷오버 후 문제 시 DNS를 구 환경으로 되돌리고, 신 환경 쓰기분은 별도 덤프로 보존한 뒤 반영 여부를 판단한다.

**11월 1일 컨테이너 이전:** 사용자 메시지의 "에네이 컨테이너"가 정확히 무엇(예: ECS/Docker on EC2/특정 제품)인지 이 세션에서 확인하지 못했다. 계획은 유지하되, 이번 구축은 **컨테이너화를 전제로 하지 않고** 현재 jar+systemd 배포 구조(`deploy-ec2.yml`)에 맞춘다. 컨테이너 이전 시 이 구성의 재사용 가능 범위(VPC·ALB·ACM·SG·SSM)와 변경 범위(EC2 크기, 레지스트리, 배포 역할)를 별도 설계한다.

**최종 확인 목록(승인 요청 전):** ① 정책·워크플로 t3.medium 일치(확인함) ② Budgets·Anomaly 켬(미확인, 사용자) ③ 구 RDS 사양·지표(미확인) ④ 루트 볼륨 20/30GB 결정 ⑤ TTL 낮춤 일정 ⑥ 컨테이너 용어 확인 ⑦ A 승인.

## 11. 2차 확정 반영 (2026-10-17, #750 병합 후 후속, 미실행)
> 변경은 문서·비활성 초안뿐이다. 신규 IAM, EC2·RDS·ALB 생성, 운영 DB·DNS 변경, 운영 배포는 하지 않았고 B(`.github/workflows/` 승격 병합)는 보류 중이다.

### 11-1. 반영한 설정
| 항목 | 반영 |
|---|---|
| EC2 | t3.medium(4GB) 유지, AL2023, IMDSv2 필수(hop 1), SSH·키페어 없음·SSM만 |
| 루트 EBS | **gp3 30GB 암호화**(`migration-infra-build.yml` VolumeSize 30. 정책의 암호화 조건은 그대로) |
| 인바운드 | EC2 8080은 **ALB 보안 그룹에서만**(YAML 195행 이미 `--source-group "$ALBSG"`, 확인함). RDS 3306은 EC2 SG에서만 |
| D(구 계정 조사 역할) | **생성하지 않음.** CloudShell 읽기 전용 방식으로 확정. 공개 로그 노출 원인이던 `migration-discovery-readonly.yml`은 **삭제**, 역할 신뢰·권한 JSON은 "미사용" 참고 자료로만 남김 |
| 조사 스크립트 | RDS CloudWatch 지표(CPU, FreeableMemory, 연결 수, 여유 용량, IOPS, 네트워크 송수신) 14일 조회 추가. 쓰기 호출 0건, `bash -n` 통과. 실행은 사용자 CloudShell에서만 |

### 11-2. 공개 Actions 로그 노출 방지 설계
이 저장소는 공개라 Actions 로그·step summary·아티팩트가 모두 공개다.
1. **운영 메타데이터(IAM 사용자명, 액세스 키 ID, KMS 키 ID, 버킷명, 호스트 영역·레코드, 비용, 계정 현황)는 Actions에서 조회·출력하지 않는다.** 조사는 CloudShell(사용자 화면)로만 한다. 이 규칙을 지키려고 조사용 워크플로를 삭제했다.
2. 구축 워크플로(`migration-infra-build.yml`)가 출력하는 값은 리소스 ID·이름·상태 정도다(VPC/서브넷/인스턴스 ID, 이름 태그). 계정 ID는 이미 저장소에 공개돼 있다. 키·비밀·DB 주소·ARN 전체·IAM 사용자 정보를 출력하는 단계를 **추가하지 않는다**(승격 PR 검토 항목: `echo`/`aws ... --output` 중 위 항목 출력 여부 grep).
3. 사용자가 CloudShell 결과를 전달할 때는 필요한 값(엔진 버전, 인스턴스 클래스, 용량, 암호화 여부, 지표 요약)만 요약한다. 액세스 키 ID·KMS 키 ID·버킷명은 채팅·PR에 붙이지 않는다.
4. 로그에 노출된 적 있는지는 기존 진단 실행 이력에서 별도로 점검한다(구 계정 `prod-diagnostic` 등이 출력한 항목 — 이번 범위 밖, 확인 필요).

### 11-3. 빌더 IAM 쓰기 권한 제거 방안 (검토 결과, 적용은 사용자 선택)
- 제거 대상: `CreateEc2SsmRoleAndProfileOnlyByName`(CreateRole·TagRole·CreateInstanceProfile·TagInstanceProfile·AddRoleToInstanceProfile), `AttachSsmCoreToEc2RoleOnly`. 결과 파일 `policy-f2r-infra-builder.no-iam-write.json`(19개 문장, 약 5.5KB) — 기존 파일은 그대로 두었다.
- 남기는 IAM 권한: `iam:PassRole`(EC2 역할 1개를 EC2에만), ELB 서비스 연결 역할 최초 1회 생성(ARN 1개 고정), Get/List 읽기.
- 워크플로 호환: IAM 단계가 이미 "없을 때만 생성"이라, 사용자가 §2 A-3으로 `f2r-ec2-ssm-role`(신뢰 `ec2.amazonaws.com`, `AmazonSSMManagedInstanceCore`)과 `f2r-ec2-profile`을 먼저 만들면 **빌더는 존재 확인만 하고 지나간다.** 미리 안 만들면 `create-role`에서 AccessDenied로 중단된다(안전한 실패).
- 효과: 빌더가 침해돼도 신뢰 정책이 임의인 역할을 만들 수 없다. 비용: 사용자 콘솔 작업 약 5분.
- **권고: 이 변형을 채택**한다. 채택 시 A 승인 범위에 EC2용 역할·프로파일 2개 생성이 추가된다(사용자가 직접).

### 11-4. 서울 리전 요금 재검증 (AWS 공개 가격 목록 JSON, 게시일 2026-10-15 기준, 월=730시간)
| 항목 | 단가 | 월 비용 |
|---|---|---|
| EC2 t3.medium Linux | $0.052/시간 | **$37.96** |
| EBS gp3 30GB | $0.0912/GB-월 | **$2.74** |
| 공인 IPv4(EC2 1 + ALB 노드 2 = 3개) | $0.005/시간/개 | **$10.95** |
| ALB | $0.0225/시간 | **$16.43** |
| ALB LCU(평균 1 LCU 가정) | $0.008/LCU-시간 | **$5.84**(저트래픽, 실측 필요) |
| S3·기타 | — | 약 $1~2(추정) |
| **소계(RDS 전)** | | **약 $75~76** |
| RDS db.t3.micro Single-AZ | $0.026/시간 | $18.98 |
| RDS db.t3.small / t3.medium | $0.052 / $0.104 | $37.96 / $75.92 |
| RDS 스토리지 20GB(gp2 요율, 보수적) | $0.131/GB-월 | $2.62 |
| **합계(micro)** / (small) | | **약 $97** / **약 $116** (+ 송신 데이터) |
| 인터넷 송신(서울→외부) | $0.126/GB(0~10TB), 월 100GB 무료(계정 합산, 기억 기반 — 확인 필요) | 데이터량에 따라 |
- 직전 추정($73~78 / $95~103)과 거의 일치한다. RDS 클래스는 §11-6 지표 확인 후 확정한다(micro 1GB는 4GB EC2의 앱에는 부족할 수 있음).
- $100 크레딧은 합계 약 $97~116 기준 **약 0.9~1개월**이다.

### 11-5. ALB 사용 여부·비용 재검토 (11월 1일 에네이 이전 반영)
오늘(10월 17일)부터 11월 1일까지 **약 15일**이다. AWS EC2 단계는 약 2주 안팎만 쓰고 에네이로 옮긴다(RDS는 남음).
| 안 | 월 비용(RDS 제외) | 장점 | 단점 |
|---|---|---|---|
| ① ALB 유지(현 설계) | 약 $75 | ACM이 TLS 관리, 헬스체크, 8080을 ALB SG로만 열기(확정 규칙에 부합), 80→443, 인스턴스 교체 용이 | 약 15일에 약 $18~20 지출 후 삭제 |
| ② ALB 없이 EIP+인스턴스 TLS(Caddy/nginx+Let's Encrypt) | 약 $46~48 | 월 약 $29 절감(약 15일 약 $15) | 80/443을 인터넷에 직접 열어야 해 **확정한 "8080은 ALB SG에서만" 구조와 달라짐**, 인증서 갱신·보안 패치 직접 관리, ACM 불가, 헬스체크·롤백 수동 |
- **권고: ①을 유지**하고, 에네이 이전 확인 직후 ALB·EC2를 정리(약 15일 비용 차이 약 $15는 보안 구조 변경 위험보다 작음). 비용이 더 중요하면 ②를 선택할 수 있으며 그 경우 SG·워크플로를 다시 설계해야 한다. **선택은 사용자 몫.**
- ALB는 삭제 방지가 켜져 있어 정리할 때 먼저 해제해야 한다.

### 11-6. JVM 힙 확정 전 확인 (확정값 아님 — 2~2.5GB는 초기 검토값)
저장소에는 `-Xmx`·`JAVA_OPTS` 설정이 **없다**(전체 검색, 0건) → 서버의 systemd 유닛·`/etc/f2r/env`에 있거나 기본값(최대 힙 = RAM의 약 1/4, 4GB면 약 1GB)일 수 있다. 같은 서버에서 도는 다른 프로세스(HWPX 워커·DesignCraft 조판 CLI 등)의 메모리도 합산해야 한다. 구 EC2에서 사용자가 SSM 세션/콘솔로 읽기 전용 확인:
`ps -eo pid,rss,cmd --sort=-rss | head`, `free -m`, `systemctl cat f2r-app`(유닛·환경 파일 경로), `grep -E 'Xmx|Xms|JAVA' /etc/f2r/env`(값 출력 시 비밀이 섞이지 않게 해당 키만), `curl -s localhost:8080/actuator/metrics/jvm.memory.max`·`jvm.memory.used`. 확정 기준: 피크 시 (JVM RSS + 워커 + OS 여유 ≥ 0.5GB)가 4GB 안. 확정 전에는 힙을 문서로 고정하지 않는다.

### 11-7. AWS Budgets·Cost Anomaly Detection 절차 (사용자 콘솔, 구축 C 전 사전 조건)
수신 대상은 **사용자가 지정한 이메일**(주 연락처 + 가능하면 보조 1명). 이메일 주소는 저장소·문서에 적지 않는다.
1. 결제 콘솔 → 결제 기본 설정: 알림 수신 켬(프리 티어/크레딧 사용량 알림 포함), **Cost Explorer 활성화**(데이터 반영까지 최대 24시간).
2. Budgets → 예산 생성 → 비용 예산(월): 금액 **$100**(크레딧 기준; 월 목표가 다르면 조정). 알림: 실제 비용 50%·80%·100%, **예상 비용 100%**. 수신자 이메일 입력. (Budgets는 알림만 하고 리소스를 정지하지 않는다.)
3. Cost Anomaly Detection → 모니터 생성(AWS 서비스 기준) → 알림 구독: 임계값 **$10(절대값)**, 빈도 즉시 또는 일일 요약, 이메일. 새 계정은 학습 기간이 필요하므로 초기에는 Budgets가 1차 방어선이다.
4. 구축 직후 Cost Explorer에서 일별 비용·태그(`Project=f2r-migration`)별 확인 — 비용 할당 태그 활성화(결제 콘솔 → 비용 할당 태그, 반영 24시간).
5. 확인 방법: 테스트 알림은 없으므로 예산 금액을 일시적으로 낮춰 알림 수신을 확인하지 않고, 구성 화면과 수신 확인 이메일(구독 확인 링크)로 검증한다.

### 11-8. 에네이(외부 컨테이너 서비스) 이전 후 검토 — 2단계 계획
**사실(사용자 제공):** 에네이는 AWS ECS가 아니라 모두의 창업 지원사업이 제공하는 외부 컨테이너 서비스. 2026-11-01부터 컨테이너 2개(각 4 vCPU·8GB RAM). 1단계는 AWS EC2(JAR+systemd), 2단계에서 앱을 에네이로 이전. **에네이의 리전·고정 송신 IP·네트워크 정책·이미지 레지스트리·DB 제공 여부는 확인하지 못했다(미확인 — 사용자 확인 필요).**
| 주제 | 검토 |
|---|---|
| RDS 연결 방식 | ① **RDS를 공개 접근 + 보안 그룹에 에네이 송신 IP만 허용**: 에네이가 고정 IP를 주는 경우에만 가능. 0.0.0.0/0 금지. `require_secure_transport=ON`, RDS CA 번들로 서버 인증서 검증, 앱 전용 최소 권한 DB 계정. ② 고정 IP가 없으면 AWS 쪽 **WireGuard/VPN 또는 SSH 터널용 소형 점프 호스트**(EC2 유지) 경유 — 복잡도·비용 증가. ③ 에네이 쪽에서 DB도 제공하면 DB를 같이 이전(AWS 송신 비용 0, 대신 데이터 이전·백업 책임 이동). 결정은 에네이 사양 확인 후 |
| 네트워크 보안 | 앱→RDS 구간 TLS 필수, 3306을 인터넷 전체에 열지 않음, 접속 로그(감사)·연결 수 제한, 앱 계정에 DDL 권한 분리(Flyway 전용 계정은 배포 시에만 사용), 비밀번호·키는 에네이 환경변수로만(저장소·로그 금지) |
| 지연 | 에네이가 서울에 가깝지 않으면 쿼리 왕복 지연이 앱 성능에 직접 영향(요청당 쿼리 수 × RTT). 이전 전에 에네이 컨테이너에서 RDS로 왕복 시간·연결 안정성을 측정 |
| 데이터 송신 비용 | 앱이 에네이(AWS 밖)에 있으면 **RDS→앱 응답이 모두 인터넷 송신**($0.126/GB, 월 100GB 무료 가정). 크기는 §11-1에서 추가한 `NetworkTransmitThroughput` 14일 값으로 산정(월 송신량 ≈ 평균 B/s × 2,592,000초). 예: 평균 50KB/s ≈ 약 130GB/월 ≈ 약 $4 초과분. 대용량 쿼리·파일을 DB에서 읽는 구조면 증가 |
| 기타 AWS 의존 | S3 배포 버킷·파일 저장을 쓰면 에네이→S3 업로드/다운로드 경로·송신 비용, 에네이→AWS 접근용 자격(장기 키 대신 가능하면 OIDC/제한 권한) 필요 |
| 에네이 이후 AWS 정리 | EC2·ALB·EIP는 정리(비용 절감), RDS는 유지하되 클래스 재산정, 백업·삭제 방지 유지 |
| 두 번 이전하는 위험 | 1단계 컷오버 후 약 2주 뒤 2단계 컷오버가 한 번 더 있다. 각 컷오버마다 TTL·롤백·쓰기 동결 절차 필요. **대안: 24시간 이전을 에네이 사양 확정 후로 조정하는 것은 사용자 판단** |

### 11-9. 신규 AWS 실제 구축 전 최종 보고 항목(승인 요청 전 점검표)
- 비용: §11-4(약 $75~76 + RDS, 합계 약 $97~116 + 송신), 크레딧 약 1개월, Budgets·Anomaly 설정 선행.
- 보안: 신뢰 정책 환경+`job_workflow_ref`, 빌더 IAM 쓰기 제거 변형(권고), EC2 SSH 없음·IMDSv2·SG 체인(ALB→8080, EC2→3306), 공개 로그 규칙(§11-2).
- 복구: §7·§8 유지, 구 RDS 최소 7일 유지·컷오버 직전 수동 스냅샷 보존.
- 미확인: 구 RDS 사양·지표, 구 EC2 메모리 사용·JVM 설정, 에네이 네트워크 사양, 구 계정 비용, 승격될 워크플로의 최종 코드.
