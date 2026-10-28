# PR #914 운영 자동화 — 최초 한 번 설치

운영 인스턴스 `i-0414de33d2803c723`, 계정 `425805163960`, 서울 `ap-northeast-2`만 대상으로 한다. AWS 액세스 키를 생성하거나 GitHub Secrets에 넣지 않는다. 기존 `F2R-914-MediaAudit` version 1은 읽고 일치 여부만 검사하며 생성·수정·삭제·CloudFormation import를 하지 않는다.

## 현재 상태와 범위

구현·로컬 검증까지 완료한 설치안이다. AWS 스택 설치, 운영 권한 확인, 실제 백업, 운영 화면 점검, PR #914 병합·배포는 아직 실행하지 않았다. 이 세션의 환경 조회에서 AWS outbound identity/secret이 없고 GitHub 커넥터는 신규 workflow_dispatch 도구를 제공하지 않았다. 이는 AWS 계정 자체에 권한이 없다는 뜻이 아니다.

PR914 승인 head: `f04b910e65cbe8ba86e7b9de2fa550b12a3084a0`. 기준 main: `df425588c97d7f1fb668e23452e638bc9cb14fd4`. 기존 승인 이미지·영상·Java·DB migration 파일은 변경 대상이 아니다. 아직 승인되지 않은 3D 테스트 모델을 추가하지 않는다.

## 관리자 초기 설정

1. 자동화 PR을 검토한 뒤 main에 병합한다. 이 설정 병합은 운영 배포를 시작하지 않는다. 기존 `deploy-ec2.yml`은 변경하지 않으며 현재 실제 트리거는 수동 workflow_dispatch이다.
2. AWS 콘솔에서 계정 `425805163960` 확인 → 리전 **아시아 태평양(서울)** → 상단 **CloudShell**을 연다. 현재 승인된 관리자/설치 역할의 임시 로그인 세션을 쓴다. 아래 작업에 필요한 권한은 마지막 절에 정리했다.
3. 이 자동화 PR의 검토된 커밋을 checkout한 저장소에서 실행한다.

```bash
bash scripts/operations/pr914/bootstrap.sh
```

복사 가능한 정확한 clone/커밋 확인 명령은 PR 본문에 제공한다. 저장소는 비밀값을 포함하지 않는 공개 자료이며, 다른 이름의 기존 작업 폴더를 덮어쓰지 않는다.

스크립트는 계정·인스턴스·태그·SSM Online, 기존 MediaAudit JSON 일치, 기존 인스턴스 역할, 작동 중인 readonly 역할의 main OIDC subject를 확인한다. 하나라도 다르면 멈춘다. 맞으면 `f2r-pr914-operations` 스택을 만들거나 동일 스택의 변경 사항을 적용한다. 기존 문서가 다르다고 자동 덮어쓰지 않는다. 오류 원인을 확인한 뒤 동일 명령을 재실행할 수 있으며 기존 정상 JAR/미디어는 이 설치 단계에서 변경하지 않는다.

생성 리소스:

- SSM: `F2R-914-AutoBackup`, `F2R-914-AutoDeploy`, `F2R-914-AutoRestore`.
- IAM: `github-actions-f2r-914-preflight`, `github-actions-f2r-914-deploy`, `github-actions-f2r-914-restore`.
- 기존 인스턴스 IAM 역할에 `F2R914ApprovedPatchRead` inline policy: 기존 배포 버킷의 `pr914-approved/*`에 대한 GetObject만 추가.

새 서버/DB/버킷/유료 생성 서비스를 만들지 않는다. S3의 작은 UI 패치, 기존 EC2의 백업 디스크와 GitHub Actions 실행은 기존 서비스 사용량에 포함된다. 계정 요금제에 따른 사용료까지 무료라고 보장하지 않는다.

4. GitHub → **Actions → PR914 automated release → Run workflow** → Branch **main**.
   - `preflight`: 기존 MediaAudit → 정상 JAR/미디어 백업 → 별도 복구 역할로 verify → 실제 운영 PC/모바일 검사. PR 병합과 배포는 하지 않는다.
   - `release`: 같은 사전검사 성공 후 기존 승인된 PR #914만 Ready/병합 → 병합 SHA의 Build Check를 신규 dispatch → 성공한 SHA의 승인 UI만 배포 → PC·360·390·430px 실제 검사. 추가 배포 승인 입력은 없다.

운영 준비가 된 뒤 바로 `release`를 선택하면 모든 사전검사를 포함하므로 두 작업을 반드시 각각 실행할 필요는 없다. 최초 이후 AWS 콘솔에서 문서를 입력하거나 백업·복구 명령을 수동 실행할 필요가 없다.

GitHub Repository Settings → Actions → General의 Actions 허용 정책에서 checkout/setup-node/upload-artifact/aws-actions를 허용해야 한다. 이 workflow는 job마다 최소 GITHUB_TOKEN permissions를 선언한다. 조직 정책·Ruleset이 봇 병합/Ready 전환을 차단하면 실패하며 우회하지 않는다. 필요한 경우 관리자에게 **이 저장소의 승인 PR 병합 및 Actions dispatch** 범위만 허용하도록 요청한다. 일반적인 사용자 토큰이나 상시 관리자 키는 필요하지 않다.

현재 Work 브라우저 연결은 없어 이 세션에서 Run workflow를 클릭할 수 없다. 로그인된 Work 브라우저를 연결하면 에이전트가 기존 승인으로 실행할 수 있다. 브라우저 연결 없이도 사용자가 Actions의 Run workflow를 한 번 눌러 전체 흐름을 시작할 수 있다.

## 배포와 복구 방식

기존 범용 배포는 Wiki/환경 키/기능 스위치도 함께 다루므로 이번 경로는 승인된 UI 파일 세 개만 교체한다.

- `static/css/f2r_home_compact.css`
- `static/js/f2r_home_presentation.js`
- `templates/public/landing.html`

`approved.json`에 PR914의 정확한 내용 SHA-256을 고정했다. 기준 운영 JAR SHA-256은 `18ca78cb7743fbd9e94e055fa5c6a4cd6fb5ca1011885b63d866f96eaa1dcdc0`이다. 다른 JAR이면 중단한다. 별도 `/var/backups/f2r-pr914-df425588`에 JAR·미디어·기존 고정 환경파일을 root 전용으로 보존하고 복사본/원본 해시를 대조한다. 이미 완전한 백업이 있으면 검증 후 재사용하며 덮어쓰지 않는다. 미완료·손상 백업은 자동 삭제하지 않는다.

배포할 패치는 기존 S3 배포 버킷의 별도 `pr914-approved/<merge-sha>/<patch-sha256>.zip` 키에 저장한다. 호스트가 패치 해시와 파일별 승인 해시를 재검증한다. 정상 백업 JAR로부터 새 JAR을 만들고, 승인 파일 외 **모든 ZIP 항목**(Java 클래스·라이브러리·DB migration·기존 이미지 포함)의 내용 해시가 같은지 확인한 다음 원자적으로 교체한다. 기존 `app.jar.previous`와 운영 미디어는 덮어쓰지 않는다. signed JAR/중복 ZIP 항목/경로 탈출/승인 외 파일은 차단한다.

JAR의 원래 `Build-Commit`은 df425588 기준으로 보존한다. UI 배포 커밋/실행 ID/실제 배포 JAR SHA는 root 백업 폴더의 `deployment.json`과 Actions 증거에 따로 기록한다. 따라서 manifest의 Build-Commit만 보고 UI 배포 실패로 판단하면 안 된다. 이 PR914 전용 방식은 임의의 향후 Java 배포를 지원하지 않는다. 다음 기능 변경은 별도 승인·정책 확장이 필요하다.

배포 역할은 AutoDeploy만, 복구 역할은 AutoRestore만 실행할 수 있다(각 역할은 MediaAudit도 허용). 두 역할 간 AssumeRole, 범용 AWS-RunShellScript, StartSession, IAM/DB/미디어 삭제 권한은 없다. 역할 신뢰는 동일 저장소의 main OIDC subject를 사용하므로 main 워크플로 수정 권한을 가진 주체에 대한 별도의 승인 주체 분리까지 제공하는 구성은 아니다. 기존 OIDC Provider와 기존 IAM 역할 정책은 변경하지 않는다.

`verify`는 실제 SSM 복구 문서를 root로 실행해 백업 해시·대상 쓰기 가능 여부·서비스 존재·수동 시작/중지 허용 설정을 확인한다. **실제 서비스 재시작/전체 복구 훈련은 아니다.** 실제 IAM/SCP·OIDC 경로도 최초 실행 성공 전에는 검증 완료로 표시하지 않는다.

배포/화면 검사 실패 시 별도 restore job이 검증된 df425588 JAR만 복구하고 서비스를 재시작해 health와 미디어 해시를 확인한다. 원본 미디어나 환경파일은 복구 경로에서도 변경하지 않는다. GitHub 실행 취소·runner 중단으로 같은 workflow 복구가 실행되지 않으면 별도 `PR914 interrupted release recovery` workflow가 보완한다. 실행 ID와 현재 JAR 해시가 다른 배포에 해당하면 덮어쓰지 않고 오류를 보고한다. 복구 자체가 실패하면 Actions 실패와 오류 annotation을 남긴다. AWS/SSM 자체 장애에서는 자동 복구 성공을 보장할 수 없다.

이 스택은 서버 전체 파일시스템을 read-only로 만들지 않는다. 고정 SSM 문서의 허용된 동작으로 원본 쓰기를 막고 배포 전후 해시를 확인한다. 웹 애플리케이션 자체의 정상 운영 DB 쓰기를 차단하는 변경도 하지 않는다. 자동화 코드에는 DB 접속·초기화·변경 명령이 없고 새로운 migration을 배포하지 않는다.

## 증거와 검사 범위

Actions Artifacts에 `pr914-backup-evidence`, `pr914-restore-permission`, `pr914-before-production`, `pr914-merge-ci-evidence`, `pr914-deployment-evidence`, `pr914-after-production`, 실패 시 `pr914-rollback-evidence`가 남는다. 백업 안의 환경파일·토큰·쿠키는 업로드하지 않는다. MediaAudit 응답이 SSM 출력 길이 제한에 걸릴 수 있으므로 완전한 미디어 manifest는 호스트의 root 백업 디렉터리가 기준이다.

브라우저 검사는 실제 공개 페이지의 캐릭터 이미지 로딩, MP4 프레임 진행, 캐릭터 전환, 메뉴 6개 응답, 미인식 질문 선택지, 처음 안내, 로그인 모달, HUD/모바일 overflow·터치 크기를 확인하고 캡처한다. POST 등 쓰기 요청을 차단한다. 로그인 성공·계정 내부 업무 처리·유료 AI 생성·신청 제출은 검사하지 않는다. 정면 얼굴의 승인 여부는 생성하지 않고 동일한 미디어 경로/해시 및 전후 캡처로 확인할 수 있도록 한다. 22% 기준은 기존 자비스 시안 대비 78%이며 예전 운영 2열 레이아웃 높이 대비 수치가 아니다.

## 최초 설치 역할에만 필요한 권한

CloudShell의 설치 주체는 다음 범위가 필요하다. 이 권한들을 GitHub 실행 역할에 붙이지 않는다.

- `sts:GetCallerIdentity`, EC2 `DescribeInstances`, SSM `DescribeInstanceInformation` 조회.
- 기존 `F2R-914-MediaAudit`에 `ssm:GetDocument`; 위 Auto 문서 3개에 CloudFormation 생성·조회·갱신·실패 정리 권한(`CreateDocument`, `DescribeDocument`, `GetDocument`, `UpdateDocument`, `UpdateDocumentDefaultVersion`, `ListDocumentVersions`, `DeleteDocument`, tag 관리).
- 신규 IAM 역할 3개의 생성·조회·trust/inline policy 등록·실패 정리·tag 관리. 기존 readonly 역할은 GetRole, 현재 EC2 profile은 GetInstanceProfile, 해당 EC2 역할은 `F2R914ApprovedPatchRead` 정책 Put/Get/DeleteRolePolicy만 필요하다. 정책 이름 자체에 대한 IAM 조건 제한은 API 지원 범위에 따라 관리자가 적용한다.
- `f2r-pr914-operations` 스택에 CloudFormation create/update/describe/change-set 실행·조회 및 ValidateTemplate. 서비스 role을 따로 쓰면 해당 설치 role에 대한 제한된 PassRole만 추가한다. 기본 스크립트는 서비스 role을 새로 만들지 않는다.

계정 SCP/permissions boundary/기존 버킷 정책이 막으면 실패 원인에 맞게 지정 리소스만 조정한다. AdministratorAccess를 GitHub 역할에 상시 부여하지 않는다. 스택을 삭제할 때 고정 SSM 문서는 Retain되어 보존되고, 기존 MediaAudit와 백업/미디어 파일은 스택의 관리 대상이 아니므로 삭제되지 않는다.
