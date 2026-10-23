# 신규 AWS 운영 진단·배포 준비

최초 기준 main `fc756729d9fced03273ff471055b28af531034ee`. 작업 중 병합된 #876/main `0698c496`을 보존해 통합했다. 병합 검증은 반복하지 않는다. 승인 범위는 코드/PR/CI 및 읽기 전용 진단이다. 운영 배포·운영 DB 적재·IAM 변경·스냅샷 생성·MCP 활성화·유료 호출은 실행하지 않는다.

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

## 자료 적재 — main #876 정본 재사용

작업 중 main에 #876(`0698c496`)이 병합되어 기존 프로토타입의 중복 production 구현을 제거했다. 배포 SHA·호스트 재입력·24시간 이내 스냅샷 이름·0600 인증·검증된 업로드 재시작·부분 등록 중단·등록 POST 재시도 금지는 #876을 그대로 사용한다. 사용법의 정본은 [운영 적재 Runbook](../studio-collaboration/PRODUCTION_IMPORT_RUNBOOK.md)이다. 이전 프로토타입의 --approval-file/--initialize-project는 제공하지 않는다.

카탈로그만 검사하는 오프라인 검증은 다음 명령이다. --production dry-run은 #876 계약대로 배포 SHA/인증을 검증하고 읽기 전용 GET을 수행하므로 실제 운영 실행은 별도 승인 후 진행한다.

```sh
python scripts/ai-agent-studio/import-production.py --source docs/ai-agent-production \
  --receipt /tmp/f2r-studio-source-dry-run.json
```

추가 보호만 결합했다: HTTP redirect를 거부해 세션 전달을 막고, 적용 전 ADMIN/원본 프로젝트/보관 여부를 확인한다. 리비전을200건씩 끝까지 읽어 기존 승인 포인터/파일/SHA를 비교하고, 등록 후536경로와 승인5종 SHA를 실제 GET으로 재조회한다. 이미 승인 해제한 캐릭터는 자동 재승인하지 않는다. 인증 쿠키는 명령행/상태/보고서에 쓰지 않는다.

업로드와 등록은 분산 트랜잭션이 아니다. 등록 실패 전 신규 파일은 남을 수 있으나 기존 상태 파일과 서버 SHA 중복 제거를 사용해 재개한다. 자동 삭제/정본 롤백을 하지 않는다. 스냅샷 이름 검사 자체는 AWS상 존재/복원 가능성을 입증하지 않는다.

## 최초 실제 운영 조회 (UTC 2026-10-22 23:04~23:08)

[새 읽기 전용 진단39869389181](https://github.com/woori73/feel2real-buildit/actions/runs/39869389181): 계정425805163960, EC2 i-0414de33d2803c723/t3.large, MemAvailable 5,561,420KiB(약5.30GiB), 앱active/running·재시작0. 현재 JAR SHA256 `177f622215a98c54d5a12837975679ef2d7f06850646eb57a8064c2e5b6e9dd8`는 [기존 배포39733429342](https://github.com/woori73/feel2real-buildit/actions/runs/39733429342)의 `2bed39181c584d8c150e3ed005592c41120319c3` artifact SHA256와 일치한다. 내장 Git 정보가 없으므로 프로세스 자체 attestation은 UNVERIFIED로 구분한다. 운영 POI는5.3.0이며 main의5.5.1은 아직 배포되지 않았다.

Deploy 역할의 RDS 조회/스냅샷 조회/quota/시뮬레이션은 AccessDenied. [기존 main 인프라 워크플로의 읽기 전용 academycraft-preflight39869694839](https://github.com/woori73/feel2real-buildit/actions/runs/39869694839)로 기존 infra-builder 권한에서 메타데이터 조회에 성공했다. RDS3개 모두MySQL8.4.8, 백업7/7/2일, 복구가능시각23:03:41/23:05:40/23:06:55UTC. 기존 available 스냅샷을 확인했으나 DB별 활성 접속 관계를 확인하기 전에는 특정 스냅샷을 운영 복구점으로 확정하지 않는다. 스냅샷은 새로 만들지 않았다.

[보완 진단39870446034](https://github.com/woori73/feel2real-buildit/actions/runs/39870446034)에서 실제 앱의 DB를 TLS_AES_256_GCM_SHA384/READ ONLY로 조회했다. 최신 Flyway는 **V1093**, 최근12행 모두success=1. V1094/V1095는 아직 운영에 없다. 다음 배포는 이 두 마이그레이션 적용을 포함하므로 **별도 운영 DB 변경 승인**이 필요하다. Deploy 역할의 RDS Describe 거부 때문에 활성 DB의 계정/인스턴스 대조는 UNVERIFIED다. 워크플로 성공은 수집 성공이며 진단 coverage는 PARTIAL로 명시된다.

IAM을 바꾸지 않는 후속 경로도 준비했다. 기존 `migration-infra-build.yml`의 보호된 infra-builder/main 경로에 고정 `step=production-readiness`를 추가했다. 같은 읽기 전용 컨트롤러만 실행하고 다른 build/provision/cutover/유료 작업은 선택하지 않는다. 이 변경이 main에 병합된 후 기존 OIDC/환경 보호를 그대로 거쳐 실행하면 RDS 접속 주소 digest 대조·정확한 활성 DB/스냅샷·권한 시뮬레이션을 조회할 수 있다. 병합 전 이 새 경로의 실제 성공은 미검증이며 보호 규칙을 우회하지 않는다.

배포 역할용 최소 읽기 권한의 **미적용 제안**은 [proposed-diagnostic-read-policy.json](proposed-diagnostic-read-policy.json). IAM 변경 승인이 아니다. RDS 목록/백업 조회와 해당 기존 역할의 권한 시뮬레이션만 제안하며 생성/수정/삭제 권한은 추가하지 않는다.

## 격리 결과

- #876 통합 후 운영 진단 정책8 및 로컬 HTTPS importer13(기존9 전부 유지+추가4) PASS. 카탈로그 오프라인536 SHA 검증 PASS.
- 배포 JAR/env 복구 가드: PASS(격리 파일/fake systemd).
- POI 파서6·정규화4·장부서비스10·기존자료 MySQL5=25건 PASS. runtimeClasspath poi/poi-ooxml/poi-ooxml-lite 모두5.5.1 확인.
- 통합 전 프로토타입의 HTTP/MySQL 검증은 historical evidence로 보존한다. 통합 후 production 대상 보호는 #876의 로컬 TLS13건으로 검증하고, 실제 MySQL의 공통 등록·재조회 경로는 opt-in 테스트로 확인한다. 운영 연결 성공으로 표현하지 않는다.
- 새 빈 격리 스키마 최초 적재678리비전/재시도0, 파일316/출처536/승인5종 검증 PASS. 기존 자료 fixture는 별도 보존.
- 마지막 변경 후 최종 CI는 PR 본문의 현재 HEAD 결과로 확인한다. 기존 결과를 새 HEAD의 PASS로 대체하지 않는다.
- CI 폰트 다운로드 병목도 수정했다. 전체 CJK apt61.2MB의 저속 다운로드를 제거하고 저장소의 기존 NotoSansKR-Regular.ttf를 사용자 폰트 경로에 설치·fontconfig로 확인한다. 원본 폰트/검증 범위는 유지하며 새 외부 패키지나 테스트 skip은 추가하지 않는다.
- 운영 배포/적재/IAM/스냅샷/MCP/유료 호출: 실행 안 함.

근거: [실제 운영 읽기 전용 JSON](readiness-20261022/aws-live-readonly.json), [Java25건](readiness-20261022/java-results.json), [격리 HTTP 최초/재시도](readiness-20261022/local-import-results.json). 인증 쿠키·DB 비밀번호·운영 사용자 데이터는 포함하지 않는다.
