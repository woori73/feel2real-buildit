# 신규 AWS 운영 진단·배포 준비

상태 기준: 2026-10-23 UTC. 최초 기준 main은 fc756729였고, 작업 중 병합된 #876(0698c496)·#875(2916d896)·#877(7f2100f8)을 보존해 정본으로 재사용한다. **운영 진단은 실제 조회 완료, 배포는 사전 스냅샷과 DB 변경 승인 전 보류**다.

## 실제 운영 결과

[정본 읽기 전용 진단39876436342](https://github.com/woori73/feel2real-buildit/actions/runs/39876436342), main2916d896에서 확인했다.

| 항목 | 관찰 결과 |
|---|---|
| 계정/EC2 | 425805163960 / i-0414de33d2803c723 / t3.large |
| 가용 메모리 | 5,409MiB(약5.28GiB), 7일 OOM0 |
| 앱/ALB | active/running, 재시작0, health200, 8080/8082 target healthy |
| 실행 JAR | 221,747,839B, SHA256 177f622215a98c54d5a12837975679ef2d7f06850646eb57a8064c2e5b6e9dd8 |
| 실행 버전 근거 | 기존 [배포39733429342](https://github.com/woori73/feel2real-buildit/actions/runs/39733429342)의 2bed39181c584d8c150e3ed005592c41120319c3과 전체 JAR 해시 일치. JAR mtime06:24:35UTC, 프로세스 시작06:24:57UTC. 내장 Git/배포 marker는 없음 |
| 이전 JAR | 존재, SHA256 a332cfa2a5858d87e3754448208c845a903992cef66d928530278a2fd5352b37 |
| 실제 접속 DB | f2r-db-202610181331, MySQL8.4.8, available, 암호화됨 |
| 백업/복구 | 보존2일, LatestRestorableTime 2026-10-23 00:06:55UTC. 해당 DB의 기존 자동 스냅샷2026-10-22 18:52:21UTC available |
| Flyway | 적용188, 최신V1093, 실패0. Studio/MCP 테이블0 |
| POI | 운영 JAR5.3.0, 후보 runtimeClasspath poi/poi-ooxml/poi-ooxml-lite 모두5.5.1 |
| 스위치 | APP_MCP_ENABLED 등 설정 없음. 본 작업의 활성화/유료 호출 없음 |

DB 식별자는 호스트의 기존 DB_HOST에서 얻었고, RDS 메타데이터 및 이전 인프라 조회39869694839와 대조했다. #877에서 실제 계정번호만 가리고 DB/스냅샷 이름을 보존하도록 같은 문제를 수정했으므로 해당 정본을 재사용한다. 중복된 마스킹 구현·테스트는 제거했고 정본의 최소권한/민감정보 비출력 테스트는 유지한다.

## 구현 범위와 정본 재사용

- #875의 prod-readonly-status/prod-diagnostic, 전용 읽기 역할·고정 SSM 문서·스냅샷 정의를 그대로 사용한다. 역할과 문서가 이미 조회 가능한 상태임을 확인했으며 **본 작업에서 IAM/SSM 설정을 생성·변경하지 않았다**. readonly-setup은 실행하지 않았다.
- #876의 production importer를 그대로 기반으로 한다. 배포 SHA·동일 카탈로그·호스트 재입력·24시간 이내 스냅샷 이름·0600 인증·상태 파일 재개·부분 등록 중단·등록 POST 재시도 금지를 유지한다. 중복된 임시 컨트롤러/워크플로/정책/CLI 구현은 제거했고 이력은 Git에 보존했다.
- 고유 보완: HTTP redirect로 ADMIN 세션을 전달하지 않음, 적용 전 ADMIN/원본 프로젝트/보관 검사, 200건씩 전체 리비전 조회, 기존 승인 포인터/파일/SHA 불변 및 등록 후536경로/승인5종 실제 GET 검증. 승인 해제본 자동 재승인 거부.
- 이후 JAR에 Build-Commit/clean·dirty 식별자를 넣어 main SHA로 운영을 추정하지 않게 한다.
- 기존 Noto Sans KR을 CI에 재사용한다. 전체61.2MB CJK apt 다운로드9분49초를 최신 CI에서2초로 줄였으며 테스트 범위는 유지한다.

## 적재 절차

사용법의 정본: [PRODUCTION_IMPORT_RUNBOOK.md](../studio-collaboration/PRODUCTION_IMPORT_RUNBOOK.md). 이전 임시 CLI의 --approval-file/--initialize-project는 제공하지 않는다. 운영 프로젝트 개설도 기존 ADMIN 화면/API를 사용한다.

오프라인 원본 검증(운영 요청 없음):

```sh
python scripts/ai-agent-studio/import-production.py --source docs/ai-agent-production \
  --receipt /tmp/f2r-studio-source-dry-run.json
```

--production dry-run은 #876 계약대로 배포 SHA/인증을 검사하고 GET만 보낸다. 실제 운영 실행은 별도 승인 후 진행한다. --apply에는 스냅샷 이름 확인이 필요하지만 **이름 검사가 AWS상 존재/복원 가능성을 보증하지는 않는다**. 실제 available 확인값만 입력한다. 인증 쿠키를 명령행·보고서·상태 파일에 넣지 않는다.

업로드와 등록은 분산 트랜잭션이 아니다. 등록 실패 전 신규 파일은 남을 수 있지만 기존 상태 파일과 서버 SHA 중복 제거로 재개하며 자동 삭제/정본 롤백을 하지 않는다.

## 스냅샷·복구와 승인 경계

기존 available 스냅샷/PITR은 조회했지만 새 사전 스냅샷은 만들지 않았다. [RDS CreateDBSnapshot 공식 API](https://docs.aws.amazon.com/AmazonRDS/latest/APIReference/API_CreateDBSnapshot.html)는 dry-run이 없다. 정책/이름 검증은 실제 생성·KMS·quota·복원 성공을 입증하지 않으므로 **생성 가능 여부 전체 검증은 미완료**다. #875의 snapshot workflow는 비활성 초안이며 활성화/생성은 별도 승인 대상이다.

다음 배포는 운영1093→V1094/V1095 적용을 포함하므로 **운영 DB 변경 승인과 사전 스냅샷 available 확인**이 필요하다. 두 migration 파일/해시는 변경하지 않았다.

기존 JAR/env 복구 가드의 격리 테스트는 PASS. 실제 운영 재시작·실패 주입·복원은 하지 않았다. JAR/env 롤백은 Flyway DB 변경을 되돌리지 않는다. DB 복원은 쓰기 중단·승인된 별도 복원 대상 검증·엔드포인트 전환과 RPO 손실 검토가 필요하다.

## 검증

- 신한카드 파서6/정규화4/장부 서비스10/기존 자료 MySQL5=25건 PASS. BIFF8/OLE2 합성 자료로 11열·별도 거래/확정일·원본 음수·합계 종료·이동 헤더·HTML 위장 거부 확인. 실제 고객 .xls 원본은 미제공으로 미검증.
- #876 기존 TLS9건을 유지하고 보완4건 포함13건 PASS. 다른 HTTPS origin의 수신 서버로 쿠키가 전달되지 않음을 실제 로컬 TLS로 시험.
- #875/#877 정본의 읽기 전용·식별자 보존14건 PASS.
- 통합 후 기존 SourceImporter MySQL5 재실행 PASS. 정본 Client의 실제 로컬 HTTP/MySQL 재조회·재실행 opt-in 검사 PASS(536경로·승인5종·포인터 불변).
- 임시 프로토타입의 빈 격리 DB 최초678리비전/재시도0 증거는 역사 기록으로 보존한다. 최신 production 보호는 #876 기반 TLS13건과 실제 공통 API 검증으로 판단하며 운영 연결 성공으로 표현하지 않는다.
- 마지막 통합 HEAD의 CI 결과는 PR #874 본문을 따른다. 이전 HEAD의 PASS를 재사용하지 않는다.

근거: [초기 운영 JSON](readiness-20261022/aws-live-readonly.json)(#875 이전 임시 진단), [Java 결과](readiness-20261022/java-results.json), [역사적 임시 CLI 검증](readiness-20261022/local-import-results.json), [현재 정본 운영 조회](https://github.com/woori73/feel2real-buildit/actions/runs/39876436342).

운영 배포·운영 DB 적재/변경·IAM 변경·새 스냅샷 생성·MCP 활성화·유료 API 호출은 본 작업에서 실행하지 않았다.
