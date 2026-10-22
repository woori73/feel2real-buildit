# AI AGENT 협업실 — 병합 결과 · 운영 준비 최종 보고

기준: main `fc756729`. 이전 `FINAL_COLLABORATION_ROOM_INTEGRATION.md`의 판정은 유지하며 통과한 격리 테스트·화면 검수는 반복하지 않았다.
**이 작업에서 하지 않은 것:** 운영 배포, 운영 DB 변경·적재, 운영 RDS 스냅샷 생성, MCP 운영 활성화, AI 직접 후보 작성 활성화, Hermes 설치, 승인 포인터 변경, 신규 AWS 리소스 생성, 유료 모델 호출.

## 1. 병합 결과 (이미 완료 상태 — 재병합하지 않고 사후 검증)

작업 시작 시 main은 이미 `ae5d1760` → `fc756729`로 진행되어 #863~#872가 **지정 순서대로 일반 병합**되어 있었다(병합 주체: 사용자 계정, 12:46~13:00 UTC). 지시한 "병합 전 최신 HEAD·CI·충돌·의존 SHA 확인"은 병합 시점이 지나서 **사후 검증**으로 수행했다.

| 검증 | 결과 |
|---|---|
| 병합 방식 | #863~#872 모두 **2-parent merge commit**(스쿼시 없음) |
| 순서·의존 SHA | 각 merge의 `^1`이 직전 병합, `^2`가 PR 최신 HEAD와 일치: #863 `09d62dee`, #864 `41b7ee73`, #865 `55da2450`, #866 `518d48a8`, #867 `94a36126`, #868 `f8dbd097`, #869 `cac911da`, #870 `6148b8d9`, #871 `38284d5d`(**Claude 수정 포함**), #872 `bd246799` |
| 자료 | `docs/ai-agent-production` 536개가 #863 HEAD와 **바이트 동일** |
| 마이그레이션 | `V1094`·`V1095`만 존재, 내용 해시 `ae5d1760`과 동일(체크섬 불변). **V1096~V1098·`ai_collaboration_*`·#862 코드 0건** |
| 내 SHA 마스킹 수정 | main에 포함 확인(`McpRedaction.DIGEST_OR_UUID`) |
| main CI | `c0b0440c`(#871 병합) Build Check ✅, **`fc756729`(최종) Build Check ✅** [run 39783157015](https://github.com/woori73/feel2real-buildit/actions/runs/39783157015). 연속 병합 중간 푸시(#864~#868)의 CI는 다음 병합이 시작되어 cancelled(정상, 최종 두 커밋이 통과) |
| #862 | Draft·미병합 유지 |

### #872 신규 변경 검토 (내가 이전에 검수하지 않은 코드, 595줄)
- 승인 포인터를 쓰는 곳은 **정확히 2곳**(`StudioService.review`의 APPROVE, `withdrawApproval`)이며 둘 다 `require(p,false,true)` = 사람 APPROVER 권한. 코드 전체 검색으로 확인. 536 적재의 승인 5건도 `review("APPROVE")`를 거친다.
- 변경: 승인본의 일반 REJECT는 409(예전엔 승인이 조용히 풀렸음), 승인 해제는 `confirmed`+현재 승인 ID 일치+사유+행 잠금+`changed==1` 확인 후에만, 감사 이벤트 `WITHDRAW_APPROVAL` 기록. 대기함·알림·댓글 조회에 새 이벤트 포함.
- 판정: **PASS(코드 검토)**. 단, 이 새 테스트(`StudioApprovalProtectionMySqlTest` 7건)를 내 환경에서 재실행하지는 않았고 CI(통합 Build Check·Studio/MCP isolated, 병합 후 main Build Check)로 갈음한다.

### AI의 승인 포인터 변경 가능성
MCP는 GET 전용(위임 JWT가 단일 GET에 묶임, 읽기 전용 트랜잭션 롤백, 쓰기 도구 없음)이고 승인 API는 사람 로그인 쿠키가 필요하다. AI가 포인터를 바꿀 경로는 **없다**. AI 직접 후보 작성(`studio:propose`)은 설계 문서(#869)뿐이며 구현·활성화 없음.

## 2. 기존 승인 자료 보존 (이전 검증 유지)
536개 SHA 무결성, 316 고유, 승인 캐릭터 5종 = MANIFEST 5개 SHA와 정확히 일치할 때만 `APPROVE` 이관(카탈로그 고정 + 저장 바이트 SHA 재검증), 콘티 승인 0, 원작 대조 0/68, 미승인 자료 승격 없음. **단, 이 이관은 운영 적재 때 실행되는 것이며 운영 DB는 건드리지 않았다.**

## 3. 운영 환경 읽기 전용 조회 결과

실행한 조회(모두 기존 읽기 전용 workflow, 조회만): `Old account probe` [run 39793489124](https://github.com/woori73/feel2real-buildit/actions/runs/39793489124), `Production Diagnostic`의 `predeploy-check` [run 39793468552](https://github.com/woori73/feel2real-buildit/actions/runs/39793468552)와 `jar-info` [run 39793479033](https://github.com/woori73/feel2real-buildit/actions/runs/39793479033). 이 세션의 AWS 자격증명은 무효(`InvalidClientTokenId`)라 직접 조회는 불가했다.

| 항목 | 결과 |
|---|---|
| **현재 운영 배포 SHA** | **`2bed39181c58…`** — 마지막 성공 `Deploy to EC2`(run 39733429342, 2026-10-22 06:27 UTC, `workflow_dispatch`). GitHub 기록 기준이며 **호스트에서 jar로 재확인하지 못함** |
| 운영 RDS 마이그레이션 버전 | **확인 못함** (아래 사유). 저장소 근거: 운영은 V1093까지로 추정 — 마지막 배포 SHA의 마이그레이션 최대 번호 |
| RDS 엔진·버전 | **확인 못함** |
| DB 백업·복구 가능 여부 | **확인 못함** (자동 백업 보존·최근 복원 가능 시점·수동 스냅샷 모두 미조회) |
| 읽기 전용 조회 가능 범위 | **현재 없음**. 아래 |

**조회가 막힌 원인(중요한 발견):**
1. 두 diagnostic workflow가 **구 계정 `067573916809`의 구 인스턴스 `i-09d7cabb5a42d1135`를 하드코딩**한다. SSM 호출이 `InvalidInstanceId: Instances not in a valid state`로 거부됨 → 이 인스턴스는 더 이상 운영 호스트가 아니거나 SSM에서 보이지 않는다.
2. `Deploy to EC2`의 기본 대상(`target`)은 **`new`(신규 계정 `425805163960`, 태그 `f2r-app`/`f2r-migration` 인스턴스)**이고 `old`는 "긴급 복구 전용"이다. 운영은 이미 신규 계정으로 옮겨졌을 가능성이 높으나, 마지막 배포가 어느 대상이었는지는 로그에서 확인하지 못했다(**사용자 확인 필요**).
3. 구 계정 배포 역할은 RDS·IAM·Route53·KMS·S3 목록 조회가 모두 `AccessDenied`.
4. **신규 계정용 읽기 전용 현황 workflow가 저장소에 없다**(신규 계정을 참조하는 것은 deploy·infra 구축·IAM bootstrap·임베딩 ops·OIDC 테스트뿐).

**조치 제안(승인 필요):** 신규 계정 읽기 전용 현황 workflow 초안을 작성했다 → `docs/reviews/handoff/workflows/prod-readonly-status.yml.txt` (describe/SELECT/systemctl show만, 쓰기 없음). 이를 main에 넣는 PR과 신규 역할의 `rds:Describe*`·`ssm:SendCommand` 권한 확인이 선행되어야 실행할 수 있다. 그때까지 RDS 버전·Flyway 현재 버전·백업 상태·배포 SHA(호스트)는 **미확인**이다.

### 배포 시 함께 나가는 변경 (main − `2bed3918`)
| 묶음 | 내용 | DB |
|---|---|---|
| #859 | 입시상담 출처 패널 정리 | 없음 |
| #854 | 통합 AI 업무 비서(쓰기·모델 플래그 기본 OFF) | V1094 |
| #853 | 금융자료 수집기(승인 가드) | 없음 |
| #855 | AI AGENT 제작 스튜디오·읽기 전용 MCP(기본 OFF) | V1095 |
| #863~#872 | 자료 536개 보존, 읽기·이미지·기억·프로젝트·초대·승인 보호 | 없음 |
- **의존성 변경(협업실 밖):** Apache POI `5.3.0 → 5.5.1` + `poi-ooxml` 추가. 기존 운영 기능 `ShinhanCardExcelParser`(신한카드 .xls)에 영향 가능 → 배포 후 샘플 명세서 스모크 필요. CommonMark 신규.
- 마이그레이션은 V1094·V1095 두 개뿐(추가형, 체크섬 `-1031052192`/`-1470147673`).
- 롤백: `2bed3918` 재배포. 이전 jar가 신규 스키마에서 기동함은 로컬 MySQL로 검증(경고만).

### 배포 직전 RDS 스냅샷 준비
`docs/reviews/handoff/workflows/rds-predeploy-snapshot.yml.txt` 초안: 환경 승인 + `SNAPSHOT-<sha8>` 확인 문자열 + 스냅샷 1개 생성·available 대기만 수행, 삭제·복원 없음. 선행 조건: `f2r-production-snapshot` 환경 생성, 신규 역할에 `rds:CreateDBSnapshot` 부여(현재 부여 여부 **미확인**), RDS 식별자는 위 현황 조회로 확보. 복원 리허설은 새 DB 인스턴스를 만들므로 별도 승인.

### 운영 적재 도구의 필요 변경 (P2, 미구현)
기존 `import-production.py --apply`는 루프백 격리 인스턴스(127.0.0.1:18090) 전용이라 **운영에 쓸 수 없다**. 필요한 변경(별도 구현·검수·승인):
1. `--production` 모드: HTTPS 기본 URL만, 기본 dry-run, 적용 시 `--confirm-snapshot <이름>`·`--confirm-sha <배포 SHA>` 필수.
2. `Origin` 헤더를 기본 URL과 동일하게, ADMIN 쿠키는 0600 파일로만(명령행·로그 금지).
3. 사전 점검(`GET /api/studio/source-import`: 0 또는 536, 프로젝트 초기화 여부), 316 업로드는 SHA 중복 제거라 이어하기 가능, 마지막에 register 1회(원자적).
4. 결과 영수증(경로 수·SHA 표·시각, 쿠키 제외) 저장.
검증 계획·사후 확인(등록 536/고유 316/승인 5/콘티 승인 0, 5종 원본 다운로드 SHA 대조)은 `COLLABORATION_ROOM_PRODUCTION_PLAN.md` §4·§7.

## 4. MCP 연결 계획 (계획만 — 활성화·실접속 없음)

현황: 협업실 MCP는 `app.mcp.enabled` 기본 **OFF**, 운영 설정에 활성화 키 없음(확인은 §3의 현황 workflow 승인 후). 공식 MCP SDK 시험은 통과(21개 읽기 전용, 이미지 블록 70,577B, 무동의 이미지 거부, SHA 보존)했으나 **실제 ChatGPT·Claude 제품 연결은 NOT TESTED**.

| 단계 | 내용 | 승인 |
|---|---|---|
| 0. 현재 | MCP OFF 유지 | — |
| 1. 선행 | 운영 배포·536 적재·접근 확인 완료. `app.admissions.rate-limit.trusted-proxies`=ALB 대역(빈 값이면 모든 익명 요청이 한 주소로 집계), `app.mcp.public-base-url`=운영 HTTPS 주소, ALB 규칙에서 `/mcp`·`/.well-known/*`·`/mcp/oauth/*`가 앱으로 전달되는지 확인 | 배포·설정 변경 승인 |
| 2. 격리 시험 | **신규 AWS 리소스 없이** 사용자 PC에서 로컬 앱(이미 적재된 격리 DB) + 임시 HTTPS 터널로 ChatGPT·Claude 실제 연결 시험(제3자 터널 사용 승인 필요). 운영 MCP는 켜지 않음 | 터널 서비스·계정 사용 승인 |
| 3. 제품별 연결 | ChatGPT 커스텀 MCP 허용 플랜 확인(자료 상충, 사용자가 앱에서 확인), Claude 커스텀 커넥터. 최소 scope `catalog:read`+`studio:read`, 원본·이미지는 별도 `originals:read` 동의. 두 제품이 같은 문서·같은 SHA를 읽는지, **이미지 판독 가능 여부**(Claude는 호출 턴에 이미지가 컨텍스트에 안 들어간다는 보고 존재)를 실측해 PASS/FAIL 기록 | 사용자 계정 사용 |
| 4. 운영 활성화 | 2·3이 통과한 뒤에만, 초기에는 ADMIN 1명·studio scope만, 연결 목록(`/mcp/connections`)에서 폐기 가능 | 별도 승인 |
| 5. 협업 규약 | AI는 `GET /api/studio/memory`의 revision·digest·근거 파일로 인계 확인, 결과는 사람이 승인. 쓰기는 `studio:propose`(미구현) 구현·동의 검증 후에만 | 별도 승인 |

**Reactor 영상 생성 MCP와의 분리 원칙** (저장소에는 Reactor 관련 코드·문서가 없어 구현은 확인하지 못함. #873 "Reactor MCP 호스트 읽기 전용 점검 workflow"는 별도 세션 작업으로 이번에 검수하지 않음):
- 서비스·호스트·OAuth 발급자(issuer)·토큰 대상(audience)·scope·속도 제한·비용 한도를 각각 분리하고 DB·저장소를 공유하지 않는다.
- 협업실 MCP는 읽기 전용 자료 조회만 제공하고 영상 생성·생성 호출 도구를 두지 않는다. Reactor MCP는 `studio_*` 데이터·승인 포인터에 접근하지 않으며, 필요한 승인 자료는 사람이 선택한 참조(SHA·파일 ID)로만 전달한다.
- 제품(ChatGPT·Claude)에는 커넥터를 두 개로 따로 등록하고, 동의 화면의 scope 이름도 구분한다.
- 외부 유료 생성 호출은 Reactor 쪽 예산·승인 규칙으로만 관리한다(협업실 쪽 예산과 합산하지 않음).

## 5. 남은 배포 조건

| # | 조건 | 상태 |
|---|---|---|
| D1 | 운영이 신규 계정인지 구 계정인지 확인(배포 대상 `new`/`old`) | **사용자 확인 필요** |
| D2 | 신규 계정 읽기 전용 현황 workflow 추가(PR·병합)와 신규 역할 `rds:Describe*`·SSM 권한 확인 → RDS 엔진·버전, Flyway 현재 버전, 백업·복원 시점, 호스트 jar SHA, 환경변수 플래그 확인 | **승인 필요 + 미실행** |
| D3 | 배포 직전 RDS 수동 스냅샷(초안 workflow 승인·권한 부여 또는 콘솔 수동 생성) | **승인 필요** |
| D4 | 배포 범위 승인(§3 표 전체, POI 업그레이드 포함)와 저트래픽 시간대, 1~2분 단절 감수 | **승인 필요** |
| D5 | 배포 후 스모크: 로그인, 학원·이지장부·입시상담, 신한카드 .xls 샘플, 스튜디오 접근(ADMIN만), `/mcp` 404 유지 | 계획 |
| D6 | 운영 적재 도구 `--production` 모드 구현·검수 → 승인 후 적재 → 사후 확인 | **미구현** |
| D7 | 승인 캐릭터 5종 이관(기존 사용자 승인 이관)에 대한 사용자 확인 | **확인 필요** |
| D8 | MCP 활성화(§4 4단계), AI 직접 쓰기(`studio:propose`), Hermes, Reactor 연동 | 각각 별도 승인 |

## 6. 미검증·위험 요약
- 운영 RDS 버전·마이그레이션 현재 버전·백업 상태·호스트 SHA·운영 환경변수: **미확인**(조회 경로 없음).
- 실제 ChatGPT·Claude 연결, 제품의 이미지 판독, AI 직접 쓰기: NOT TESTED / 비활성.
- 새 #872 테스트의 독립 재실행: 하지 않음(CI 갈음).
- 구 계정 진단 workflow(`prod-diagnostic`, `ec2-diagnose`, `diagnose-ec2-readonly` 등)는 구 인스턴스를 가리켜 현재 운영 진단에 쓸 수 없음 — 정리 필요.
- ADMIN은 모든 프로젝트의 승인권자(설계), 536개 열람·승인 권한이 관리자 계정에 집중됨.
