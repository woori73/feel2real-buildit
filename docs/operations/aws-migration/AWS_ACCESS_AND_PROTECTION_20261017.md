# 기존 AWS 접근 경로 확인 · GitHub 보호 설정 · 신뢰 정책 확정 절차 (2026-10-17)

> 선행 문서: `AWS_INFRA_REVIEW_20261017.md`(PR #741 병합). 이 문서도 **실행·AWS 변경 없음**(조사·준비). 비밀 값 없음.
> 이 세션의 AWS 자격 증명은 STS가 거부(`InvalidClientTokenId`)하여 AWS를 직접 조회하지 못했다. 아래 "확인"은 **GitHub Actions 실행 이력·로그, 저장소 파일, GitHub 저장소 메타데이터**에 근거한다.

## 1. 기존 AWS 접근 경로 (새 장기 액세스 키를 만들기 전에)
| 경로 | 확인된 사실 | 한계 |
|---|---|---|
| A. GitHub OIDC 역할 `arn:aws:iam::067573916809:role/github-actions-f2r-deploy` | 2026-10-16 01:08 UTC 실행(run 38833505889)에서 **가정 성공**("Authenticated as … GitHubActions"). 기존 진단 워크플로가 이 역할로 구 운영 EC2 `i-09d7cabb5a42d1135`에 **SSM `SendCommand`** 를 사용 | **`rds:CreateDBSnapshot` AccessDenied**(같은 로그): 이 역할에는 RDS 스냅샷 권한이 없다 → 스냅샷 생성·공유는 이 경로로 불가. EC2 인스턴스 프로파일은 정본(`docs/AWS_DEPLOYMENT.md`)상 S3 최소 권한뿐이고 정적 키가 없다 → SSM으로도 RDS·KMS·Route53은 못 본다 |
| B. 사용자 콘솔 로그인(MFA) | 사용자가 계정을 인수해 접근 가능(사용자 진술) | 에이전트가 쓸 수 없음. RDS 스냅샷·공유, KMS 키 정책, Route53/가비아 변경은 사용자 승인·실행 또는 아래 C |
| C. (제안) 구 계정에 **읽기 전용 OIDC 조사 역할** `f2r-migration-discovery` 신설 | 신규 계정과 같은 보호(Environment `f2r-aws-discovery` + `job_workflow_ref` main + 불변 ID `sub`). 정책 `policy-old-account-migration-discovery.json`(40개 액션, 쓰기·객체 읽기 0), 신뢰 `trust-old-account-migration-discovery.json`, 워크플로 `migration-discovery-readonly.yml` | **구 계정 IAM 변경**이므로 사용자 승인 + 사용자가 콘솔에서 생성. 스냅샷 생성·공유·KMS 권한은 별도 승인 시점에 별도 문장으로 추가 |
- **새 장기 액세스 키는 발급하지 않는다.** OIDC(C)로 해결 가능하며, 키 유출 위험이 없다. 구 계정에 이미 있는 IAM 사용자·액세스 키는 C의 조사(`list-access-keys` 메타데이터)로 현황을 확인하고, 불필요하면 사용자 판단으로 비활성화한다.
- 못 확인한 항목: 구 EC2·RDS·S3·Route53·KMS의 실제 설정, 구 IAM 사용자/키 현황, 구 계정 월 비용 — 위 조사 워크플로(승인 후)가 한 번에 출력한다(RDS 엔진 버전·암호화·KMS 키, 스냅샷 공유 가능 여부 포함).

### 발견: 구 운영 배포 역할의 신뢰 정책이 넓을 가능성 (구 계정 변경이므로 승인 필요)
- 2026-10-16 01:08 UTC에 **비-main 브랜치**(`ci/rds-snapshot-predeploy`)의 워크플로가 이 역할을 가정했다. 즉 현재 신뢰 정책은 `sub`를 main으로 고정하지 않는 것으로 보인다(와일드카드 추정, **신뢰 정책 원문은 미확인**).
- 영향: 이 저장소에 push 권한이 있는 주체가 아무 브랜치에서나 워크플로를 만들어 **운영 EC2에 SSM 명령을 보내고 배포 버킷에 쓸 수 있다**(공개 저장소이지만 push는 쓰기 권한 필요 — 에이전트·협업자 계정 위험).
- 권고(승인 후): 구 계정 `github-actions-f2r-deploy` 신뢰 정책을 `sub=repo:woori73@299170593/feel2real-buildit@1340651018:ref:refs/heads/main` + `job_workflow_ref`(deploy·진단 워크플로 목록)로 고정. 신뢰 정책 원문은 C의 조사 출력으로 확인한 뒤 수정안을 만든다. **인증 실패 시 신뢰 정책을 임의로 완화하지 않는다.**
- 참고(`sub` 형식 이력): 2026-10-16 09:09 UTC 실측(run 38878468985)에서도 `sub`는 저장소 ID 포함 형식이었고 기존 형식 패턴과는 불일치(`False`)였다. 같은 날 01:08에 가정이 성공했으므로 구 역할 신뢰 정책은 ID 형식과 호환된다.

## 2. GitHub 보호 설정 가능 여부 (저장소 메타데이터 기준)
- 저장소 `woori73/feel2real-buildit`는 **공개(public) 저장소이고 개인 계정 소유**, 이 세션의 토큰은 admin 권한(`permissions.admin=true`).
- 공개 저장소는 **Environment required reviewers·배포 브랜치 제한·브랜치 보호/Ruleset·CODEOWNERS가 모든 플랜에서 사용 가능** → 요금제 제한 없음(비공개로 바꾸면 유료 플랜 필요).
- 이 세션의 GitHub 도구에는 Environment·Ruleset 설정 API가 없어 **직접 적용할 수 없다.** 사용자가 아래 순서로 설정한다(약 5분).

### 2-1. Environment `f2r-infra-build` (Settings → Environments → New environment)
1. 이름 `f2r-infra-build`
2. **Required reviewers**: `woori73`(사용자) 추가
3. **Prevent self-review: 끔** — 모든 에이전트가 같은 GitHub 계정(`woori73`)으로 워크플로를 실행하므로, 켜면 사용자가 자신이 트리거한 실행도 승인하지 못한다
4. **Deployment branches and tags → Selected branches → `main`**
5. 구 계정 조사용 `f2r-aws-discovery`도 같은 방식으로 생성(승인 시점에)

### 2-2. main 보호 (Settings → Rules → Rulesets → New branch ruleset, 대상 `main`)
- 켤 것: **Require a pull request before merging**, **Require status checks**(`build`, `mysql-integration-test`), **Block force pushes**, **Restrict deletions**
- **켜지 말 것(지금은)**: *Require review from Code Owners*와 승인 리뷰 수 ≥1 — **에이전트와 사용자가 같은 계정이라 자기 PR을 승인할 수 없어 모든 병합이 막힌다.** (이 저장소의 PR은 모두 `woori73` 명의로 올라간다.) 계정 분리(아래) 후 켠다.
- CODEOWNERS는 파일로 미리 준비 가능(켜지 않으면 효력 없음): `/.github/workflows/ @woori73`, `/docs/operations/aws-migration/ @woori73`

### 2-3. 이 구조의 한계와 권고
- 승인 게이트의 실효는 "사람이 UI에서 승인"에 달려 있다. 에이전트는 **승인 API를 호출하지 않는다**(운영 원칙)이나, 같은 계정의 토큰은 기술적으로 가능하므로 **에이전트 전용 계정(또는 GitHub App)과 사용자 계정을 분리**하는 것을 권고한다. 분리하면 required reviewers·Code Owners·Prevent self-review를 모두 켤 수 있다.
- 신뢰 정책이 `environment` + `job_workflow_ref`(main)에 묶여 있어, 워크플로에서 `environment:` 줄을 빼면 `sub`가 달라져 **역할 가정이 거부**된다(권한 확대가 아니라 실패). 그러나 `environment`를 유지한 채 main의 워크플로 내용을 바꾸는 변경은 승인자가 "무엇을 승인하는지" 확인해야 막을 수 있다 → 승인 전 해당 실행의 커밋을 본다.

## 3. Environment 적용 후 `sub` 확정 → 신뢰 정책 확정
1. 사용자가 2-1을 완료했다고 알린다.
2. 클레임 확인 워크플로(`oidc-claims-env.yml`)를 브랜치 `ci/oidc-claims-env`에 `.github/workflows/`로 복사해 push → Environment 승인 요청이 사용자에게 오고, 승인하면 실행(AWS 접근 없음, 공개 클레임만 출력).
3. 출력 `sub`가 `repo:woori73@299170593/feel2real-buildit@1340651018:environment:f2r-infra-build`와 같으면 `trust-f2r-infra-builder.json`을 **그대로 확정**. 다르면 **출력된 실제 값으로만** 교체한다(불변 ID 형식 유지, 와일드카드·`StringLike` 완화 금지).
4. 확정 후 사용자 승인 아래 IAM 역할 생성 → `validate` 단계로 가정 확인. AccessDenied가 나면 원인을 조사한다(`job_workflow_ref` 지원은 AWS 공식 문서로 확인됨 — 사용자 확인). 신뢰 정책을 임의로 완화하지 않는다.


### 3-1. 확정 결과 (2026-10-17, 일회성 무-AWS 실행 run 39030186213)
- 실측 `sub` = `repo:woori73@299170593/feel2real-buildit@1340651018:environment:f2r-infra-build`, `environment` = `f2r-infra-build`, `aud` = `sts.amazonaws.com` → **`trust-f2r-infra-builder.json`의 `sub` 값과 정확히 일치. 신뢰 정책은 수정 없이 확정**(불변 ID 형식 유지, 완화 없음).
- (보호 설정 전 상태) 같은 실행이 승인 대기 없이 9초 만에 완료되고 비-main 브랜치에서도 Environment를 쓸 수 있었다 → 사용자가 아래 설정을 적용했다.

### 3-2. 보호 설정 적용 후 검증 결과 (2026-10-17, AWS 접근 없는 일회성 워크플로 `env-protection-check.yml`)
적용된 설정(사용자 보고): Required reviewers `woori73`, Prevent self-review 끔, 관리자 우회 끔, Deployment branches `main`만.
| 시나리오 | 결과 | 근거 |
|---|---|---|
| 비-main 브랜치(`ci/env-protection-check`)에서 Environment job | **step이 한 번도 실행되지 않고 1~2초 만에 실패**(3회 일관: run 39034117340 attempt 1·2, run 39034460705) | job에 `steps`·로그 없음(러너 미할당) |
| 같은 실행 안의 대조군 job(Environment 없음) | **정상 실행**(step 3개 완료) | 브랜치·러너·워크플로 자체는 정상 |
| 보호 설정 전 같은 방식의 비-main 실행 | 통과(run 39030186213) | 바뀐 것은 Environment 설정뿐 |
| main(`a267a75b`)에서 Environment job | **`waiting` 유지(30초 이상)**, 대조군은 완료 | run 39034932819 — 승인 대기(사용자 승인 필요) |
- 해석: 승인자만 설정된 경우의 결과는 "무기한 waiting"이므로, 비-main의 **즉시 실패**는 배포 브랜치 제한에 의한 거부로 판단한다(설정 전 통과 → 설정 후 실패의 차분 + 대조군). **단, GitHub 화면의 거부 문구(annotation)는 이 도구로 읽지 못했다** — 확정 증거가 필요하면 해당 run 페이지의 job 주석을 사용자가 확인한다.
- 정정: 비-main 실행 직후 한 번 `waiting`으로 관측되었으나 1~2초 후 실패로 바뀐 일시 상태였다(재조회로 확인). 판정 근거는 최종 상태만 쓴다.
- **미완료(승인 필요)**: main 실행 승인 후 정상 실행 여부(`ENV-PROTECTION-CHECK-REACHED`와 environment `sub` 출력) — 사용자가 승인한 뒤 기록한다.

### 3-3. 일회성 검증 워크플로 제거 계획
- 검증 완료 후 PR로 `.github/workflows/env-protection-check.yml` 제거(이 파일은 main push·dispatch로 Environment를 호출하므로 오래 두지 않는다). 같은 파일을 건드리는 push는 다시 승인 요청을 만들 수 있다.
- 일회성 브랜치 `ci/oidc-claims-jwr`, `ci/oidc-claims-env`, `ci/env-protection-check`는 삭제하지 않고 보관(삭제는 별도 승인). 기존 `ci/oidc-claims-once`도 동일.
- 기존 운영 배포용 OIDC 신뢰 정책(구 계정)은 이 작업에서 변경하지 않았다.

## 4. 뮤즈 인계 (신규 인프라 구축 담당)
- 전달물(main에 병합됨, PR #741): `docs/operations/aws-migration/` — `trust-f2r-infra-builder.json`, `policy-f2r-infra-builder.json`, `trust-github-actions-f2r-deploy.json`, `policy-f2r-deploy-smoke-ssm.json`, `migration-infra-build.yml`, 검토서 `AWS_INFRA_REVIEW_20261017.md`.
- 뮤즈가 할 일(승인 후): ① 정책·워크플로를 **수정본 기준**으로 사용(원안 사용 금지: `sub` 형식·SSM 권한·ALB 단계) ② 사용자가 Environment를 만든 뒤 `.github/workflows/migration-infra-build.yml`로 배치하는 PR 작성 ③ 단계 순서 `validate → vpc → ec2 → rds-shell → alb → acm → (DNS 검증 레코드 등록은 클로드/사용자) → alb-https → smoke` ④ 실행마다 사용자 Environment 승인 ⑤ 구축 결과·설정 문서를 클로드에게 인계.
- 뮤즈가 하지 않는 일: RDS 복원, 운영 데이터가 있는 작업, DNS 전환, 운영 배포(클로드 총괄, 사용자 최종 승인).
- 뮤즈는 준비가 끝나면 **사용자에게 최종 인프라 구축 승인을 요청**한다(IAM 역할 생성·인프라 구축은 그 승인 전까지 실행하지 않는다).
- 대시보드 인계(`claude-handover-dashboard-20261017.md`): PR #740(`70cd68e9`)이 main에 병합됨(마이그레이션 없음, `/dashboard`·`/api/dashboard/**`, `/admin/hub` 개선). 배포 후보 SHA는 이 병합으로 `88e94d1a` → **`70cd68e9` 이후**로 갱신되며, 신규 AWS 배포·검증은 클로드가 담당한다. 배포 전 `/dashboard` ADMIN·TUTOR 접근, TUTOR 타 학원 사업체 403, 비로그인 차단을 인계서 §7 절차로 확인한다.
