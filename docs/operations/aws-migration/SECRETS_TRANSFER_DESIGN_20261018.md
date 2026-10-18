# 비밀값 이전 설계 — 공개키 암호화 자동화 (2026-10-18, 설계·초안만, 미실행)

> **이 문서와 `secrets-transfer.yml`은 실행하지 않았다.** 실제 비밀값 읽기·복호화·이전은 별도 승인 후에만 한다. 채팅·PR·로그·아티팩트에 값이 나오지 않는 것이 설계 조건이다.

## 1. 이전 대상 (코드 근거: `deploy-ec2.yml`, `docs/AWS_DEPLOYMENT.md`)
- **옮기지 않는 값:** `META_VISION_API_KEY`·`META_JIPJIGI_API_KEY`·`KIS_APP_KEY/SECRET`·`OPENROUTER*`는 배포 워크플로가 **GitHub Secrets에서 S3 staging을 거쳐 서버의 `/etc/f2r/env`에 다시 주입**한다 → 신규 서버도 같은 배포로 주입된다(전달 불필요). `HWPX*`·`ASSIST*` 스위치도 배포가 관리.
- **옮기지 않고 교체할 값:** RDS 접속 정보(주소는 신규 RDS로 바뀜. 계정·비밀번호는 스냅샷 복원으로 유지되므로 주소만 교체).
- **옮길 값:** 서버에만 있는 `/etc/f2r/env` 나머지(예: `JWT_SECRET`, `S3_*`, OAuth 등 — 키 이름은 읽지 못해 목록 미확인). 워크플로는 제외 목록 정규식 외 전부를 대상으로 한다.
- **줄이기:** `JWT_SECRET`은 새로 발급해도 된다(사용자 재로그인, Wiki identity HMAC은 배포가 이 값에서 재생성). 외부 서비스 키는 각 서비스 콘솔에서 재발급이 가능하면 이전 대신 재발급.

## 2. 흐름 (`secrets-transfer.yml`)
1. **prepare(신규 계정, Environment 승인):** 임시 RSA-3072 키쌍 생성, 개인키는 `/f2r/transfer/privkey`(SecureString)에 저장, **공개키만** 출력.
2. **export(구 계정, 기존 `github-actions-f2r-deploy`, 환경 없음·`needs: prepare`):** 구 서버에서 SSM으로 스크립트 실행 → env에서 제외 키 제거 → 무작위 AES 키로 암호화 → 그 AES 키를 공개키(RSA-OAEP)로 암호화 → **암호문만** 표준 출력. 평문·키 이름·값은 출력하지 않음.
3. **import(신규 계정, Environment 승인):** 개인키로 복호화(메모리 파일시스템 `/dev/shm`, 권한 0600) → 값마다 `::add-mask::`를 **저장 전에** 등록 → `/f2r/app/<KEY>` SecureString 저장(`--value file://`로 프로세스 목록 노출 방지) → 종료 시 파일 `shred`, **개인키 파라미터 삭제**. 출력은 개수뿐.
4. (배포 단계, 별도) 신규 EC2가 자기 역할(`f2r-ec2-secrets-read`: `/f2r/app/*` 읽기 전용)로 파라미터를 읽어 `/etc/f2r/env`를 구성. 값은 SendCommand 파라미터에 넣지 않는다(SSM 명령 이력에 평문이 남기 때문).

## 3. 검증 (오프라인, 가짜 값)
- 가짜 env 6줄(제외 키·따옴표 값·빈 값 포함)로 export 스크립트→암호문→복호화→파싱 전체를 로컬 openssl로 왕복 시험: **출력에 평문 0건**, 제외 키 제거, 복호화 성공. 빈 값은 SSM이 저장할 수 없어 건너뛰도록 수정.
- `bash -n`·YAML 파싱 통과. **AWS 호출·실제 파일·실제 값은 사용하지 않았다.**
- 미검증: 실제 SSM 전송 한도(표준 출력 24,000자 — env가 작아 문제 없을 것으로 추정), 구 배포 역할이 해당 인스턴스에 `SendCommand`·`GetCommandInvocation` 권한을 가졌는지(기존 진단 워크플로가 같은 호출을 쓴다는 근거만 있음).

## 4. 노출 경로와 대응
| 경로 | 평가 |
|---|---|
| GitHub Actions 로그(공개) | 평문 미출력, 값 마스킹 선등록, 키 이름 미출력. 암호문은 출력 job 간 전달(출력값)이며 개인키 없이는 무의미 |
| SSM 명령 이력(구 계정) | 암호문과 공개키만 기록. 스크립트에 값 없음 |
| 아티팩트·PR·채팅 | 사용하지 않음 |
| 실행기 디스크 | `/dev/shm`(메모리), 0600, 종료 시 `shred` |
| 신규 계정 | SecureString(`aws/ssm` 키), 접근은 `/f2r/*` 한정(구축 역할 쓰기, EC2 역할 `/f2r/app/*` 읽기) |
- 남는 위험: 실행기 메모리에는 복호화된 값이 잠시 존재(같은 job 안). 승인된 main 워크플로만 실행 가능.

## 5. 이 PR의 부수 변경(실행 없음, 모두 비활성 초안·정책 파일)
- `policy-f2r-infra-builder.json`: `/f2r/*` 파라미터 Put/Get/Delete 문장 1개 추가(기존 19→20개 문장).
- `policy-f2r-ec2-secrets-read.json`(신규), `policy-new-account-bootstrap.json`·`new-account-bootstrap.yml`: EC2 역할에 읽기 전용 인라인 정책을 넣을 수 있게 확장(대상 역할 이름 한정은 유지).
- 주의: 이 변경으로 부트스트랩 역할이 EC2 역할에도 인라인 정책을 넣을 수 있게 된다(역할 이름 고정·신뢰 정책 변경 불가·7일 만료는 그대로).

## 6. 구 배포 역할 신뢰 정책을 main으로 제한할 때의 영향 (정적 검증)
- 근거: 저장소의 AWS 워크플로 16개 모두 이 역할(`github-actions-f2r-deploy`, 구 계정)을 쓰고, **전부 `workflow_dispatch` 단독 트리거이며 `environment:`를 쓰는 job이 없다.**
- 제안 신뢰 정책(`trust-old-deploy-role-main-only.proposed.json`, 적용하지 않음): `aud`=`sts.amazonaws.com`, `sub`=`...:ref:refs/heads/main`, `job_workflow_ref`=`woori73/feel2real-buildit/.github/workflows/*@refs/heads/main`(StringLike).
- **영향 없음:** main에서 dispatch하는 16개 워크플로(`deploy-ec2.yml` 포함)는 `sub=ref:refs/heads/main`이라 계속 통과. 이 문서의 `secrets-transfer.yml` export job도 환경 없이 main에서 실행되어 통과.
- **영향 있음:** ① **main이 아닌 브랜치에서 dispatch하는 실행**(과거 진단·점검용 `ci/*` 브랜치 실행)은 거부된다 → 먼저 main에 병합한 뒤 main에서 실행해야 한다. ② 어떤 job이든 `environment:`를 추가하면 `sub`가 `environment:<이름>`이 되어 거부된다(구 역할을 쓰는 워크플로에는 환경 사용 금지).
- 한계: **현재 신뢰 정책 원문을 읽지 못해** 제안이 현 정책보다 좁다는 것은 과거 가정 이력(비-main 가정 성공)에 근거한 추정이다. 적용은 구 계정 IAM 변경(사용자 콘솔에서 신뢰 정책 편집)이며 별도 승인 후에만 한다. 적용 직후 main에서 읽기 전용 진단 워크플로 1건으로 가정 성공을 확인하고, 실패 시 기존 정책 원문(적용 전 사용자가 복사해 둠)으로 되돌린다.
