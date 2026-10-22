# 신규 AWS 운영 진단·배포 준비

기준 main `fc756729d9fced03273ff471055b28af531034ee`. 병합 검증은 반복하지 않는다. 승인 범위는 코드/PR/CI 및 읽기 전용 진단이다. 운영 배포·운영 DB 적재·IAM 변경·스냅샷 생성·MCP 활성화·유료 호출은 실행하지 않는다.

## 진단

`prod-diagnostic.yml`과 이를 호출하는 `diagnose-ec2-readonly.yml`은 신규 계정 425805163960/서울/기존 github-actions-f2r-deploy 역할에 고정된다. Name=f2r-app, Project=f2r-migration, running EC2가 정확히 한 개여야 실행하며 구 계정 fallback은 없다. 기존 prod-diagnostic의 배포/롤백/유료 AI 선택 항목은 제거했다. 원본은 기준 커밋 Git 이력에 보존된다. 다른 구 계정 진단 워크플로는 이번 실행 대상이 아니다.

```sh
gh workflow run prod-diagnostic.yml --repo woori73/feel2real-buildit --ref fix/aws-production-readiness
```

고정된 SSM payload만 사용하며 임의 명령/SQL 입력은 없다. SSM 실행 기록은 생성되지만 서비스·설정·DB를 변경하지 않는다. 메모리, systemd 필드, 현재/이전 JAR SHA256·내장 Git SHA·POI 라이브러리를 읽는다. env 파일을 실행하거나 출력하지 않는다. 검증된 신규 EC2 앱의 기존 RDS 접속 설정으로 TLS READ ONLY 트랜잭션에서 Flyway 최근12행만 조회한다. TLS cipher를 확인하며 RDS 제어면 조회 권한과 DB의 SELECT 권한을 구분한다. RDS 계정/인스턴스는 접속 주소의 SHA256를 Describe 결과와 대조한 경우에만 MATCHED로 표시한다. RDS가 여러 개이므로 f2r-db를 활성 DB로 가정하지 않는다. 패스워드는 자식 환경으로만 전달하고 로그/오류/프로세스 인자를 출력하지 않는다.

JAR에 Git 정보가 없으면 실행 SHA는 UNKNOWN이다. main 또는 성공한 배포 run SHA를 그대로 운영 SHA로 단정하지 않는다. 이후 bootJar에 clean/dirty 및 Build-Commit을 기록하며 진단은 프로세스 시작·JAR 시각을 교차 확인한다. 과거 JAR은 배포 로그의 전체 SHA256 대조가 별도로 필요하다.

RDS 상태·백업 보존기간·최신 복구 시각·기존 스냅샷·quota를 읽고, 허용되는 경우 IAM 시뮬레이션으로 CreateDBSnapshot 권한을 평가한다. RDS CreateDBSnapshot은 dry-run을 지원하지 않는다. 시뮬레이션 allowed도 SCP/KMS/조건/quota 및 실제 생성 성공을 보장하지 않는다. 권한 부족은 UNVERIFIED로 보고하며 IAM을 수정하거나 스냅샷을 만들지 않는다.

## 복구

승인 전 EC2 MemAvailable·재시작 수·실행 SHA/JAR 해시·Flyway 버전, RDS available·최신 복구 시각, 이전 JAR/env 백업과 체크섬을 확인한다. 사전 스냅샷 생성과 available 확인은 별도 승인 후 수행한다. `scripts/deploy/f2r_app_guard.test.sh`는 fake systemd/임시 파일에서 JAR 실패 복구와 env 내용·소유자·권한 복원을 검증한다. 운영 실패 주입/재시작은 하지 않는다.

JAR/env 롤백은 Flyway의 DB 변경을 되돌리지 않는다. DB 복원이 필요하면 신규 쓰기를 중단하고 승인된 별도 대상으로 복원·검증한 후 엔드포인트를 바꾼다. 스냅샷 이후 쓰기의 RPO 손실 위험을 승인자에게 제시한다.

## POI·신한카드

poi/poi-ooxml 5.5.1을 유지한다. HSSFWorkbook의 실제 BIFF8/OLE2 합성 fixture로 11열·거래일/확정일 분리·원본 음수 부호·합계 종료를 검증한다. 이동한 헤더, HTML 위장 xls 거부를 추가한다. 고객의 실제 파일은 제공되지 않았으므로 해당 운영 원본의 성공을 주장하지 않는다. 금융기관 접속/장부 쓰기는 없다.

## 자료 적재

다음 production dry-run은 운영 HTTP 요청 없이 로컬 원본536개/고유316 SHA만 검사한다.

```sh
python scripts/ai-agent-studio/import-production.py --production \
  --source docs/ai-agent-production --receipt /tmp/f2r-studio-dry-run.json
```

운영 `--apply`에는 고정 HTTPS https://feel2real.co.kr, 별도 사용자 승인 파일, owner-only ADMIN 세션 파일이 필요하다. 승인 파일은 사용자 의사의 운영 기록이며 인증/서버 권한 검사를 대신하지 않는다. 비밀번호/토큰은 PR·명령행·receipt에 넣지 않는다. 도메인의 신규 계정 배치 관계도 실제 진단 후 승인한다.

승인 파일 필드: approved=true, action=studio-source-import, account=425805163960, origin=https://feel2real.co.kr, projectId=ai-agent-part1, sourceCommit=09d62dee982e2e89e3653c90b1ee42f05bff4f9d, approvedBy, reference. 이 PR은 실제 승인 파일을 제공하지 않는다. 프로젝트 부재 시 명시적 `--initialize-project`도 필요하다.

도구는 파일/리비전을200개씩 끝까지 읽고 기존 SHA를 재사용한다. 저장 바이트를 GET해 SHA·크기를 확인하고 checkpoint를 남긴다. 등록 후 source-import 상태와 전체 리비전을 재조회해536경로·승인5 SHA·기존 승인 포인터/파일 보존을 확인해야 성공한다. 승인 해제한 캐릭터는 자동 재승인하지 않는다. 기존 출처/멱등/승인 이관/작업 기억 보존 API를 재사용한다.

HTTP redirect는 거부하며 production proxy/TLS 검증을 유지한다. 실패 시 종료2와 마지막 확인 stage/fileMapping을 남긴다. 업로드와 등록은 분산 트랜잭션이 아니므로 등록 실패 시 신규 파일이 남을 수 있다. 재시도는 SHA로 재사용하며 자동 삭제/정본 롤백을 하지 않는다.

## 최초 실제 운영 조회 (UTC 2026-10-22 23:04~23:08)

[새 읽기 전용 진단39869389181](https://github.com/woori73/feel2real-buildit/actions/runs/39869389181): 계정425805163960, EC2 i-0414de33d2803c723/t3.large, MemAvailable 5,561,420KiB(약5.30GiB), 앱active/running·재시작0. 현재 JAR SHA256 `177f622215a98c54d5a12837975679ef2d7f06850646eb57a8064c2e5b6e9dd8`는 [기존 배포39733429342](https://github.com/woori73/feel2real-buildit/actions/runs/39733429342)의 `2bed39181c584d8c150e3ed005592c41120319c3` artifact SHA256와 일치한다. 내장 Git 정보가 없으므로 프로세스 자체 attestation은 UNVERIFIED로 구분한다. 운영 POI는5.3.0이며 main의5.5.1은 아직 배포되지 않았다.

Deploy 역할의 RDS 조회/스냅샷 조회/quota/시뮬레이션은 AccessDenied. [기존 main 인프라 워크플로의 읽기 전용 academycraft-preflight39869694839](https://github.com/woori73/feel2real-buildit/actions/runs/39869694839)로 기존 infra-builder 권한에서 메타데이터 조회에 성공했다. RDS3개 모두MySQL8.4.8, 백업7/7/2일, 복구가능시각23:03:41/23:05:40/23:06:55UTC. 기존 available 스냅샷을 확인했으나 DB별 활성 접속 관계를 확인하기 전에는 특정 스냅샷을 운영 복구점으로 확정하지 않는다. 스냅샷은 새로 만들지 않았다.

배포 역할용 최소 읽기 권한의 **미적용 제안**은 [proposed-diagnostic-read-policy.json](proposed-diagnostic-read-policy.json). IAM 변경 승인이 아니다. RDS 목록/백업 조회와 해당 기존 역할의 권한 시뮬레이션만 제안하며 생성/수정/삭제 권한은 추가하지 않는다.

## 격리 결과

- 정책14 및 production dry-run536 SHA: PASS.
- 배포 JAR/env 복구 가드: PASS(격리 파일/fake systemd).
- POI 파서6·정규화4·장부서비스10·기존자료 MySQL5=25건 PASS. runtimeClasspath poi/poi-ooxml/poi-ooxml-lite 모두5.5.1 확인.
- 실제 로컬 HTTP/MySQL에서 production 분기(테스트만 origin을 loopback으로 고정)2회 완료. 각316개 저장 바이트 SHA 재조회·536경로·승인5종·기존정본 불변, createdRevisions0. 실제 운영 연결 아님.
- 새 빈 스키마의 첫 적재/재시도 및 최종 CI/운영 Flyway: 확인 후 갱신.
- 운영 배포/적재/IAM/스냅샷/MCP/유료 호출: 실행 안 함.
