# 신규 AWS 인프라 구축 보안 검토 (Muse 인계 `claude-handover-infra-review-20261017.md` 대상)

> 검토 범위: 인계 문서의 신뢰 정책·권한 정책·`migration-infra-build.yml`·비용. **IAM 역할 생성·인프라 구축·AWS 변경은 실행하지 않았다.** (이 세션의 AWS 자격 증명은 STS가 거부해 AWS 읽기 조회도 불가 → 모든 판정은 문서·정책 텍스트와 GitHub 실측 토큰 기준)
> 수정본(이 디렉터리): `trust-f2r-infra-builder.json`, `trust-f2r-infra-builder.fallback-branch.json`, `trust-github-actions-f2r-deploy.json`, `policy-f2r-infra-builder.json`, `policy-f2r-deploy-smoke-ssm.json`, `migration-infra-build.yml`
> 승인 의견(요약): **원안 그대로는 부적합(차단 결함 3건), 수정본 + 선행조건 충족 시 진행 가능.**

## 0. 차단 결함 (원안 그대로 실행하면 실패하거나 위험)
| # | 결함 | 영향 | 수정 |
|---|---|---|---|
| B1 | **신뢰 정책 `sub` 값이 실제 토큰과 다름.** 실측 `sub` = `repo:woori73@299170593/feel2real-buildit@1340651018:ref:refs/heads/<ref>` (소유자·저장소 **숫자 ID 포함**). 원안은 `repo:woori73/feel2real-buildit:ref:refs/heads/main` | 역할 가정이 항상 거부(AccessDenied). 2026-10-16 OIDC 장애와 같은 계열일 가능성 | 수정본 trust 3종은 실측 형식으로 교체 |
| B2 | **권한 정책에 SSM 권한이 없다.** §2b 제목은 "SSM 추가분"이나 내용에 `ssm:*` 없음, §2a는 신뢰 정책의 중복. YAML은 `ssm:GetParameter`·`SendCommand`·`GetCommandInvocation` 사용, 설계 근거는 "Project 태그 인스턴스로만 SendCommand"라 했지만 정책에 그 제한이 존재하지 않음 | AMI 조회·smoke 전부 실패, 또는(보완 시 `*`로 열면) 임의 인스턴스 명령 실행 | 수정본 정책에 태그·이름 조건 + 문서 `AWS-RunShellScript` 한정 SSM 문장 추가 |
| B3 | **ALB 구성이 미완.** 대상 등록(`register-targets`) 없음, 443 리스너 단계 없음(80→443 리다이렉트만), 헬스체크 기본값 `/` | 컷오버 시 사이트 불가·헬스 실패. `/`는 ALB가 30초마다 홈을 호출해 **방문 통계(PV)를 오염**(SiteVisitFilter) — 정본 `docs/AWS_DEPLOYMENT.md`는 `/actuator/health`(permitAll), 포트 8080 | `alb`에 대상 등록·속성(유휴 180초 등), 신규 `alb-https` 단계, 기본 헬스 `/actuator/health` |

## Q1. OIDC `job_workflow_ref`
- **실측(2026-10-17, 일회성 무-AWS 워크플로, run 39028273681):** 토큰에 `job_workflow_ref` 클레임이 있고 값 형식은 `woori73/feel2real-buildit/.github/workflows/<파일>@<ref>`(ID 미포함). `migration-infra-build.yml`을 main에서 실행하면 `woori73/feel2real-buildit/.github/workflows/migration-infra-build.yml@refs/heads/main`이 되어 원안의 값과 **형식이 일치**한다. `aud=sts.amazonaws.com`, `iss=https://token.actions.githubusercontent.com`. 토큰에 `environment` 클레임은 environment 미사용 시 없음.
- **`sub` 불일치(B1)**: 위 표 참조. `StringLike`를 `StringEquals`로 바꾸고 `sub`·`aud`·`job_workflow_ref`를 **한 객체**에 둔다(중복 키 없음, JSON 검증 완료).
- **AWS가 이 조건 키를 지원하는지는 AWS 공식 문서로 확인하지 못했다**(문서 도메인 접속 불가). 확인된 것: AWS가 2026-02-02 발표로 GitHub 등 일부 공급자 고유 클레임을 신뢰 정책 조건 키로 지원(검색 결과 기준), 지원 목록 전문은 미확인. 미지원 키는 **조건이 거짓이 되어 가정이 거부(fail-closed)** 되므로 우회 위험은 아니고 구축이 막힐 위험이다.
  - 대응: 역할 생성 직후 `validate` 실행 → AccessDenied면 `job_workflow_ref` 조건만 제거(`trust-...fallback-branch.json`)하고 **Environment 보호로 통제**.
- **우회 가능성 분석**
  - 다른 브랜치/포크 PR: `sub`(ref 또는 environment)와 `job_workflow_ref`의 `@refs/heads/main`이 달라 거부.
  - 같은 저장소의 **다른 워크플로 파일**: `job_workflow_ref`가 다르면 거부(지원 시). 미지원이면 `sub`만 남으므로 environment 고정이 실질 방어선.
  - **main에서 워크플로 자체를 수정**하는 경우가 남는 위험(아래 Q3).
- **신뢰 정책 정정안(권장)**: `sub`를 environment로 고정(`...:environment:f2r-infra-build`). ※ environment 형식의 `sub`는 ref 형식의 실측으로부터 추정한 값이며, **environment를 만든 뒤 클레임 확인 워크플로를 한 번 더 실행해 정확한 문자열을 확정**해야 한다.

## Q2. 권한 정책 (원안 → 수정)
| 항목 | 원안 | 수정 |
|---|---|---|
| 삭제·종료 | `Delete*`·`Terminate`·`Stop`·`Reboot`·`ReleaseAddress`·`DeleteLoadBalancer`·`acm:DeleteCertificate`·`iam:Delete*` 등 다수 | **전부 제거** (정리는 MFA 관리자가 콘솔에서). 자동 점검: 정책에 Delete/Terminate/Stop/Reboot/Release/Detach/Revoke/Restore/CreateDBInstance/ModifyDB*/s3/`iam:*` 계열 **0건** |
| 이번 단계 불필요 | `rds:CreateDBInstance`·`ModifyDBInstance`·`ModifyDBParameterGroup`, EBS `CreateVolume/Attach/Detach`, EIP 5종, `CreateRule/ModifyListener/ModifyTargetGroup`, `SecurityGroupEgress`, `DeleteTags` | 제거. 단, ALB가 새로 필요로 하는 `RegisterTargets`·`Modify*Attributes`·`iam:CreateServiceLinkedRole`(ELB 최초 사용, 서비스명 조건)·`ssm:DescribeInstanceInformation`·`iam:Get*/ListAttachedRolePolicies` 추가 |
| `Resource:"*"` | EC2·RDS·ELB 전 액션 | Describe류·`CreateVpc` 등 **생성 계열만** `*` (AWS가 리소스 단위 미지원). 생성은 `aws:RequestTag/Project=f2r-migration` 필수, 변경은 `aws:ResourceTag/Project` 조건 + 리소스 ARN, `CreateTags`는 `ec2:CreateAction` 한정(기존 리소스 재태깅으로 조건 우회 불가). RDS·ELB는 `f2r-*` ARN. 남은 `*` 문장 5개: Describe, 태그 조건 Create*, TagOnlyAtCreation, ACM RequestCertificate(도메인 조건), SSM 결과 조회 |
| `RunInstances` | 모든 리소스 `*` | 인스턴스: `t3.small`·IMDSv2 필수·프로파일 `f2r-ec2-profile`·Project 태그 조건 / 볼륨: 암호화 필수 / 서브넷·SG: Project 태그 / AMI: `ec2:Owner=amazon` |
| `iam:PassRole` | `role/f2r-*` + instance-profile ARN(잘못된 리소스 유형) | **`role/f2r-ec2-ssm-role` 하나만, `iam:PassedToService=ec2.amazonaws.com`** |
| IAM 생성 | `f2r-*` 와일드카드, `Delete*` 포함 | 정확한 이름 2개(`f2r-ec2-ssm-role`, `f2r-ec2-profile`)만 생성, `AttachRolePolicy`는 `AmazonSSMManagedInstanceCore` 1종 유지 |
| S3 | 버킷 3개 `CreateBucket`·정책·암호화 | **제거**(버킷은 이미 생성됨, 이 단계 불필요). ALB 액세스 로그를 쓸 때 해당 버킷 정책만 후속 단계로 추가 |
| ACM | `Resource:*`, Delete 포함 | `RequestCertificate`는 `acm:DomainNames`가 `feel2real.co.kr`·`*.feel2real.co.kr`일 때만, Delete 제거 |
| 크기 | — | 인라인 약 6.1KB(한도 10,240자) |
- 자동 대조: 수정 YAML이 호출하는 AWS API **49개 전부 정책에 허용됨(누락 0)**, 정책 안 미사용 액션은 태그·PassRole·SLR 등 암묵적 필요분뿐(불필요한 2개는 제거).
- 한계: 이 정책은 **IAM Access Analyzer `validate-policy`·실제 `RunInstances` 권한 평가로 검증하지 못했다**(자격 증명 없음). EC2의 리소스 수준 조건(특히 `RunInstances` 보조 리소스, `AuthorizeSecurityGroupIngress`의 SG 규칙 리소스)은 첫 실행에서 AccessDenied가 나면 해당 문장만 조정한다 — 실패는 항상 **거부 방향**이라 과권한으로 번지지 않는다.

## Q3. 반복 실행·부분 실패 (YAML)
원안 결함과 수정:
- VPC 생성 후 `modify-vpc-attribute`, 서브넷 생성 후 공인 IP 속성, IGW 생성 후 attach, 라우트 테이블 생성 후 route·associate가 **"없을 때만 한 묶음"** 이라, 중간 실패 후 재실행하면 **DNS 호스트명·공인 IP 속성·IGW 연결·기본 라우트·서브넷 연결이 영구히 빠질 수 있었다.** → 수정본은 항목별로 상태를 조회해 빠진 것만 보정(속성은 매번 적용).
- SG 규칙 `|| true`가 **권한 오류까지 삼킴** → `InvalidPermission.Duplicate`만 성공 처리.
- IAM: 역할만 만들고 실패하면 재실행이 `EntityAlreadyExists`로 영구 중단 → 역할·정책 연결·프로파일·프로파일 내 역할을 각각 확인.
- 인스턴스 프로파일 전파 지연(`sleep 10` 고정) → 재시도 루프 + `--client-token`(같은 요청 중복 생성 방지), 인스턴스 2개 이상이면 중단.
- 동시 실행으로 VPC·ALB가 중복 생성되는 경합 → `concurrency: f2r-infra-build`(대기, 취소 안 함).
- `_sg`/`_subnet`이 "None"을 그대로 다음 명령에 넘김 → `need` 가드로 명확한 오류.
- **ALB 대상 등록·443 리스너 추가**(B3), ACM 목록에 상태 필터·검증 레코드 대기 루프·`--idempotency-token`.
- smoke: SSM 등록(Online) 확인 후 명령(미등록 인스턴스에 보내면 실패), **두 역할 모두 성공하도록** 빌더 역할 smoke + **별도 job**(`smoke-deploy-role`)에서 배포 역할 smoke. job 간 값은 `outputs`로 전달(원안의 `GITHUB_ENV`는 job 안에서만 유효).
- **워크플로 변경 시 권한 확대 위험**: 역할 ARN·environment를 **입력이 아니라 고정**(입력으로 다른 역할 지정 불가). 신뢰 정책이 environment + `job_workflow_ref`(main)에 묶이므로, 이 파일을 main에서 바꾸는 사람이 곧 역할 사용자다 → **필수 조치**: main 브랜치 보호(PR 리뷰 필수), `.github/workflows/migration-infra-build.yml` CODEOWNERS = 사용자, environment `f2r-infra-build`에 **required reviewers(사용자) + 배포 브랜치 main 한정**. 워크플로 수정과 실행 권한이 분리된다(실행은 승인 필요).
- 검증 결과(로컬 정적): YAML 파싱 OK, 모든 `run` 블록 `bash -n` OK, shellcheck(warning 이상) 지적 1건(미사용 변수) 수정 후 0. **실제 AWS 실행 검증은 하지 못했다**(승인 후 `validate`→단계별).

## Q4. 네트워크·보안
- EC2: SSH·키페어 없음 유지, IMDSv2 필수 + **hop limit 1**, **루트 볼륨 암호화**(수정), 인바운드는 ALB SG의 8080만. RDS: 비공개 서브넷(공인 라우트 없음), 3306은 EC2 SG에서만, 퍼블릭 접근 없음(복원 시 `--no-publicly-accessible` 명시 필수 — 복원은 별도 승인).
- ALB: 80/443 공개(필수), 80→443 리다이렉트, 유휴 180초(정본 요구), 잘못된 헤더 폐기, 삭제 방지 켬, TLS 정책 `ELBSecurityPolicy-TLS13-1-2-2021-06`.
- 남는 위험(수용 여부 판단 필요): EC2가 공인 IP를 가진 공개 서브넷(NAT 비용 회피, 대신 SG로 차단). 사설 서브넷+NAT/VPC 엔드포인트는 월 $22~35 추가.
- **SSM 원격 실행 범위(Q5)**: `SendCommand`는 ① 인스턴스 ARN + `ssm:resourceTag/Project=f2r-migration` **그리고** `Name=f2r-app` ② 문서 `AWS-RunShellScript`로 한정. 구축 직후 관리자가 인스턴스 ARN을 **정확한 ID로 고정**하는 2차 정책을 권장(태그 기반은 같은 태그로 인스턴스를 더 띄울 수 있으나 `RunInstances`가 단일 모양·단일 프로파일로 제한되고 재태깅은 불가). 명령 본문은 고정 스크립트이며 입력을 받지 않는다. 배포 역할도 같은 제한(`policy-f2r-deploy-smoke-ssm.json`).
- **배포 역할 신뢰 정책**(`trust-github-actions-f2r-deploy.json`): `sub`는 main ref(실측 형식), `job_workflow_ref`는 `deploy-ec2.yml`·`migration-infra-build.yml`(smoke 전용, **smoke 완료 후 후자 제거**).
- 초기 구축 역할에서 삭제·종료·복원·DB 생성 권한은 제거됨. 그 작업은 승인 후 별도 역할/관리자 콘솔에서.

## Q5 비용 (Seoul, 이 세션에서 AWS 가격 페이지를 열 수 없어 **기억 기반 추정치 — 확정 전 AWS 요금 계산기로 재확인**)
원안 월 $43의 누락 항목:
| 항목 | 원안 | 검토 |
|---|---|---|
| EC2 t3.small | $20.80 | 약 $19~21 (타당) |
| **EBS gp3 20GB** | 누락 | 약 $1.8 |
| **공인 IPv4** | 누락 | 2024-02부터 모든 공인 IPv4 유료: EC2 1개 + **ALB 노드 2개** = 3개 × 약 $3.65 = **약 $11** |
| ALB 고정 + LCU | $16.43 + ~$5 | 타당 (저트래픽 기준 LCU $3~6) |
| S3 | $1~2 | 타당(데이터 이전 시 증가) |
| **인터넷 송신 데이터** | 누락 | 월 100GB까지 무료, 초과 약 $0.12/GB — 영상 트래픽이 크면 지배 항목(500GB ≈ $48) |
| AZ 간 전송 | 누락 | ALB(2AZ)→EC2(2a 1대) 일부 AZ 간, GB당 $0.01 — 소액 |
| 합계(RDS 복원 전) | $43 | **약 $55~59 + 송신 데이터** |
| RDS 복원 후(db.t3.micro 20GB) | 약 $65 | **약 $77~85 + 송신 데이터** (인스턴스 크기는 구 RDS 확인 후 결정 — t3.micro 1GB는 작을 수 있음, 크면 증가) |
- **크레딧 $100**: 복원 후 월 약 $80 수준이면 **약 1.2~1.3개월**. Budgets $90 알림은 알림일 뿐 정지하지 않음 → 50/80/100% 예측 알림 + Cost Anomaly Detection 권장.
- **구/신 동시 운영 기간**: 신규 약 $80/월 **+ 기존 계정 비용(미확인 — Cost Explorer 읽기 필요, 사용자 소유 자격 증명 확보 후)**. 컷오버 후 구 환경 유지 기간(롤백 대기 7일 가정)만큼 이중 비용 → 신규 월 $80 + 기존 월 비용 × 7/30. 부분 실패 시 비용: NAT 없음, 유료로 남는 것은 ALB·공인 IPv4·EC2·EBS(생성 후 삭제 전까지 시간당 과금) — 삭제 방지 켠 ALB는 정리 시 속성 해제 필요.
- RDS 복원 후에는 단일 AZ·단일 EC2(가용성 낮음)임을 수용해야 한다.

## 선행조건 (승인 의견의 전제)
1. GitHub Environment `f2r-infra-build` 생성(required reviewers=사용자, 배포 브랜치 main) — GitHub 설정 변경, AWS 아님.
2. 환경 생성 후 클레임 확인 워크플로를 한 번 더 실행해 environment `sub` 문자열 확정 → 확정값으로 trust 수정.
3. main 브랜치 보호 + CODEOWNERS(워크플로 파일).
4. 구 RDS 엔진 버전 확인 후 `db_param_family` 선택(스냅샷과 같은 패밀리여야 함, 8.0 스냅샷은 8.4 파라미터 그룹으로 복원 불가).
5. 사용자 최종 승인 후에만 IAM 역할 생성 → 즉시 `validate` 실행(가정 성공 확인, `job_workflow_ref` AccessDenied 시 fallback).
6. 위 수정본이 main에 병합된 뒤 그 파일을 `.github/workflows/`에 배치(이 디렉터리 파일은 활성 워크플로가 아님).

## 증거
- OIDC 실측: GitHub Actions run `39028273681`(브랜치 `ci/oidc-claims-jwr`, AWS 접근·시크릿 없음, JWT 원문 미출력)
- 정책 대조·JSON(중복 키 없음)·YAML·shell 검증: 이 세션 로컬 실행 결과
- 미검증: AWS 측 `job_workflow_ref` 조건 키 지원 범위, 실제 `AssumeRoleWithWebIdentity`, IAM 평가, 가격(확정치)
