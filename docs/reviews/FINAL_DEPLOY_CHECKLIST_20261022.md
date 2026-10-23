# 최종 배포 체크리스트 (신규 계정, main `fc756729`)

작성: 2026-10-22. 이 문서는 계획이다. **배포·DB 변경·스냅샷 생성·IAM/SSM 생성·병합은 하지 않았다.** 아래 각 단계의 실행은 별도 승인 후에만 한다.

## 0. 확인된 운영 현황 (읽기 전용 근거)

### RDS 3개 (2026-10-22 23:07 UTC, 사용자가 실행한 `academycraft-preflight` [run 39869694839](https://github.com/woori73/feel2real-buildit/actions/runs/39869694839))

| 식별자 | 역할 | 엔진 | 백업 보존 | 최근 복원 가능 시점 | 저장 | 근거 |
|---|---|---|---|---|---|---|
| **`f2r-db-202610181331`** | **운영(앱이 사용)** | MySQL 8.4.8 | **2일** | 23:06:55 | 20GB | 최종 전환 [run 39131938382](https://github.com/woori73/feel2real-buildit/actions/runs/39131938382) `cut-new-db`가 이 이름(STAMP=202610181331)으로 복원 → 앱 env의 RDS 호스트 교체 → 216표 행 수 일치·`app-healthy` → `cut-dns` 성공 |
| `f2r-db-202610181240` | 실패한 전환 잔여 | MySQL 8.4.8 | 7일 | 23:05:40 | 20GB | [run 39128226823](https://github.com/woori73/feel2real-buildit/actions/runs/39128226823) `cut-new-db` 실패 → `cut-rollback`(DNS를 구 ALB로). 당시 구 운영이 다시 쓰기를 받았으므로 데이터가 10-18 12:40 시점에 멈춤 |
| `f2r-db` | 리허설 복원본 | MySQL 8.4.8 | 7일 | 23:03:41 | 20GB | 10-18 08:49 `db-migrate`(run 39112927403) 복원 |

- 세 개 모두 `db.t4g.micro`, available(10-22 `validate` [run 39867933372](https://github.com/woori73/feel2real-buildit/actions/runs/39867933372)), 자동 스냅샷이 매일 생성 중.
- 전환 이후 앱 env의 DB 호스트를 바꾸는 단계는 확인 문자열이 필요한 `p2-cutover`뿐이고 다시 실행되지 않았다(`restore-rds`는 `f2r-db` 생성만 하고 env를 바꾸지 않음). → **운영 DB = `f2r-db-202610181331`로 확정.** 바이트 수준 재확인은 #875 승인 후 `db_instance_identifier` 출력으로 한다.
- 수동 스냅샷: `before-admissions-20261021`(10-21 12:34), `f2r-shared-20261018`, `f2r-shared-final-202610181240`, `f2r-shared-final-202610181331`.
- **위험: 운영 DB의 백업 보존이 2일**(다른 두 개는 7일). 전환 단계 주석에 "백업 보존 설정은 전환 후 별도 적용"이라 되어 있으나 적용 단계가 없다. 배포 전 7일로 올리는 것을 권고한다(무중단 설정이지만 **DB 설정 변경이므로 별도 승인**).
- DB 연결: 최대 60, 사용 18·최고 20. 앱이 마스터 사용자(`f2r_admin`, 전권 + GRANT OPTION)로 접속한다 → 앱 전용 최소 권한 사용자 분리를 별도 과제로 권고(공개 로그에 사용자 이름이 이미 출력됨).
- Flyway: 전환 시점 `flyway_schema_history` 180행(전환 로그). 실행 중 SHA `2bed3918`은 V1093까지 포함하고 부팅 성공(`FlywayRepairConfig`가 매 부팅 repair→migrate) → **현재 V1093까지 적용, V1094·V1095 미적용으로 판단**(직접 조회는 #875 승인 후).

### 월 예상 비용 (서울, 온디맨드 단일 AZ, 공개 요금 기준 추정 — Billing에서 확인 필요)

| 항목 | 1개당 | 3개 |
|---|---|---|
| db.t4g.micro 인스턴스 (약 $0.025/시간 × 730시간) | 약 $18 | 약 $55 |
| 스토리지 20GB (gp2/gp3 약 $0.13/GB-월) | 약 $2.6 | 약 $8 |
| 소계 | **약 $21/월** | **약 $63/월** |

- 잔여 2개(`f2r-db`, `f2r-db-202610181240`)를 정리하면 약 **$42/월** 절감. 자동 백업은 인스턴스 저장 용량까지 무료라 정리 시 추가 절감은 작고, 수동 스냅샷 4개(각 최대 20GB, 약 $0.095/GB-월)는 따로 남는다.
- **삭제하지 않았다.** 삭제 전 최종 스냅샷을 남길지 포함해 소유자가 결정한다.

### 앱·호스트
- EC2 `i-0414de33d2803c723` t3.large, available 5,423MB(run 71), f2r-app·위키 active, AcademyCraft(8082)도 같은 호스트에서 실행 중.
- 실행 중 SHA: `2bed3918`(jar 시각·배포 기록 일치, 바이트 증명 아님).

## 1. 배포 전 (D-1 ~ 직전)

- [ ] #875 승인: SSM 문서 `F2R-ReadOnlyStatus`·역할 `github-actions-f2r-readonly` 생성 → 병합 → `Prod read-only status` `section=all` 실행. 확인: `db_instance_identifier=f2r-db-202610181331`, Flyway `max_version=1093`·실패 0, V1094/V1095 행 없음, `studio_or_mcp_tables=0`, 플래그 `APP_MCP_ENABLED`·`BUILDIT_UNIFIED_ASSISTANT_WRITE_ENABLED` 상태
- [ ] (권고·승인 필요) 운영 DB 백업 보존 2일 → 7일
- [ ] 배포 범위 승인: `2bed3918..fc756729` = #859, #854(V1094), #853, #855(V1095), #863~#872, POI 5.3.0→5.5.1·poi-ooxml·CommonMark
- [ ] 저트래픽 시간 선택(단일 대상이라 재시작 약 1분 동안 요청 실패 가능)
- [ ] 배포 직전 수동 스냅샷 `f2r-predeploy-YYYYMMDDHHMM-<sha8>` (대상 `f2r-db-202610181331`) — 스냅샷 workflow 활성화 또는 콘솔. `available` 확인 후 이름 기록
- [ ] MCP·AI 쓰기 스위치 OFF 유지 확인(기본값 OFF)

## 2. V1094·V1095 적용 계획

- 두 파일 모두 **새 테이블 CREATE만**(V1094: `unified_assistant_*` 3개, V1095: `studio_*` 6개 + `mcp_*` 7개, 합계 16개). 기존 테이블 변경·데이터 수정 없음 → 기존 기능 영향 없음, 잠금 짧음.
- 적용 방식: 새 jar 부팅 시 Flyway가 자동 적용(repair → migrate). 별도 수동 SQL 없음.
- 확인: 배포 후 `Prod read-only status`(flyway-history)에서 1094·1095 `success=1`, 체크섬 V1094 `-1031052192`, V1095 `-1470147673`.
- 실패 시: MySQL DDL은 트랜잭션이 아니므로 V1095가 중간에 실패하면 **일부 테이블이 남는다**. 가드가 이전 jar로 되돌리면 이전 jar는 repair로 실패 행을 지우고 V1093 기준으로 정상 부팅한다(V1094·V1095는 이전 jar가 모르는 버전 → 무시). 재배포 전에는 남은 `studio_*`/`mcp_*` 테이블 정리(DB 변경, 승인 필요) 또는 스냅샷 복원이 필요하다.

## 3. 배포

- [ ] `Deploy to EC2` (main, `target=new`, 다른 입력은 `unchanged`)
- [ ] 가드 `f2r_deploy_with_rollback`: `app.jar.previous` 백업 → 교체 → PID 추적 health 재시도 → 실패 시 자동으로 이전 jar 복구. 로그에서 `healthy` 또는 `rolled back` 확인

## 4. 기존 운영 SHA(`2bed3918`)로 복구

| 상황 | 방법 |
|---|---|
| 배포 중 health 실패 | 가드가 자동으로 `app.jar.previous`(= 2bed3918 jar) 복구. 추가 조치 없음 |
| 배포는 성공했으나 기능 결함 | main에 되돌림 PR(병합 커밋 revert) 병합 → `Deploy to EC2`. **주의: 배포 역할 신뢰가 `refs/heads/main`만 허용하므로 태그·다른 브랜치로 2bed3918을 배포할 수 없다**(이전 보고 D5의 "2bed3918 ref로 재실행"은 정정). 되돌린 코드는 V1094·V1095 테이블을 무시하므로 DB 되돌림 불필요 |
| 데이터 손상 | 배포 직전 스냅샷에서 **새 인스턴스로** 복원 → env의 DB 호스트 교체(별도 승인, 쓰기 작업). 스냅샷 이후 쓰기는 유실되므로 최후 수단 |

## 5. 배포 후 스모크 (각 항목 결과 기록)

| 영역 | 확인 |
|---|---|
| 공통 | `/actuator/health` 200(ALB 대상 healthy), 홈 200, 정적 자원·JS 오류 없음, 로그에 Flyway 1094·1095 적용·`ERROR` 없음 |
| 로그인 | 일반 계정 로그인/로그아웃, JWT 쿠키 HttpOnly, 잘못된 비밀번호 거부, ADMIN 로그인 |
| 학원 | `/academies` 목록·상세, 원장 계정으로 학원 관리 화면, `academy.feel2real.co.kr`(AcademyCraft, 별도 대상 그룹) 영향 없음 |
| 장부 | `/easy`(에듀홀릭 장부)·`/ledger`(이지장부) 열람, 기존 거래·분개 건수 변화 없음, **신한카드 `.xls` 미리보기(확정 전)** |
| 입시상담 | `/admissions/guide`·`/admissions/workspace` 열람, 질의 1건 응답(검색·근거 표시), 임베딩 서버 상태 |
| 신규(꺼진 상태) | `/ai-agent-studio` ADMIN만 접근, MCP 엔드포인트 비활성(OFF), 통합 비서 쓰기 OFF |

## 6. 신한카드 실제 `.xls` 샘플 검증

- 저장소에는 실제 파일이 없다(개인정보). POI 5.3.0·5.5.1로 비-POI 작성기(LibreOffice·xlwt) 신한카드형 `.xls` 2개 파싱 결과는 완전히 동일했고 기존 테스트 18건 통과(`handoff/poi/`).
- 사용자가 실제 다운로드 원본 1~2개로 운영(또는 로컬 `./scripts/run-local.sh`)에서 **미리보기만** 실행하고 확정하지 않는다. 확인: 건수, 합계 금액, 승인일·가맹점·금액 열, 카드 끝 4자리(숫자 셀이면 `1234.0`으로 보이는 기존 동작 — 회귀 아님).
- 이상이 있으면 그 파일로 회귀 테스트를 만들되 원본은 커밋하지 않는다(값을 바꾼 재현본만).

## 7. 536개 자료 적재 순서 (#876, 배포 후·별도 승인)

1. 배포 SHA가 `fc756729` 이후(카탈로그 `production-09d62dee.json` 포함)이고 최근 성공 `Deploy to EC2`와 같은지 확인
2. `/ai-agent-studio`에서 ADMIN이 프로젝트 개설
3. 배포 직전 스냅샷 이름 확인(`f2r-predeploy-...-<sha8>`, 24시간 이내)
4. `#863` 원본 추출(`--extract-to`) → ADMIN 쿠키를 0600 파일로
5. `--production` **dry-run**: `toUpload=316`, `alreadyStored=0` 확인
6. 승인 후 `--apply --confirm-snapshot <이름>`. 중단되면 같은 명령 재실행(검증된 파일 건너뜀, 등록 1회)
7. 사후 확인: 등록 536 / 고유 316 / 승인 캐릭터 5(MANIFEST SHA 일치) / 콘티 승인 0 / 원작 대조 0/68. 쿠키 파일 삭제, 영수증 보관
8. D7(승인 캐릭터 5종 이관) 결과를 사용자가 확인

## 8. 하지 않은 것 · 승인 대기

IAM 역할·SSM 문서 생성, PR 병합, 배포, DB 설정 변경(백업 보존 포함), 스냅샷 생성, RDS 삭제, MCP 활성화, 운영 적재, 영상 생성 — 모두 하지 않았다.

## 9. 읽기 전용 진단 실측 (2026-10-22 23:59 UTC, [run 39875278441](https://github.com/woori73/feel2real-buildit/actions/runs/39875278441))

준비: #875 병합(`2916d896`) → `readonly-setup` [run 39875077560](https://github.com/woori73/feel2real-buildit/actions/runs/39875077560)으로 SSM 문서 `F2R-ReadOnlyStatus`(버전 1, Active)와 역할 `github-actions-f2r-readonly`를 만들었다(관리형 정책 0개, 인라인 `f2r-readonly-status` 1개). 배포 역할은 변경하지 않았다.

| 항목 | 결과 |
|---|---|
| 운영 DB | 호스트 env 기준 **`db_instance_identifier=f2r-db-202610181331`** (확정) |
| MySQL | 8.4.8 |
| Flyway | 적용 188건, **최대 V1093, 실패 0**. V1090~1092는 10-19 04:10, V1093은 10-21 00:58. **V1094·V1095 미적용**, studio/mcp 테이블 0 |
| 백업 | 운영 DB 보존 **2일**(나머지 7일), 최근 복원 가능 시점은 약 3분 전. 운영 DB 자동 스냅샷은 매일 18:52 UTC. 수동 스냅샷 `before-admissions-20261021`은 운영 DB에서 찍은 것 |
| RDS 공통 | 3개 모두 db.t4g.micro, 암호화 True, MultiAZ False, **삭제 보호 False**(권고: 운영 DB에 켜기, 승인 필요) |
| 앱 | f2r-app active/running, 재시작 0, 시작 10-22 06:24:57. health 200, ALB 대상 8080·8082 healthy |
| jar | `app.jar` 221,747,839 B, 수정 06:24:35, SHA-256 `177f6222…9dd8`. `app.jar.previous` 221,743,820 B, `a332cfa2…2b37`. 배포 표식 `app.jar.meta` 없음 |
| 운영 SHA | **`2bed3918`(Deploy run 39733429342, 06:24:33 업로드 → 06:24:35 jar → 06:24:57 기동과 시각 일치)**. 배포 로그에 jar 해시가 남지 않아 바이트 대조는 하지 못했다 |
| 자원 | available 5,427MB, 디스크 26G 여유, OOM 0(7일) |
| 플래그 | `APP_MCP_ENABLED`·`ANTHROPIC_ENABLED`·`APP_ADMISSIONS_RATE_LIMIT_TRUSTED_PROXIES`·`BUILDIT_UNIFIED_ASSISTANT_WRITE_ENABLED` 모두 env에 없음 → 코드 기본값(OFF) |
| 비밀값 노출 | 없음(DB 사용자·비밀번호·호스트·키 출력 없음) |

발견: Describe 출력의 계정 번호 마스킹이 RDS 이름의 12자리 시각까지 가렸다 → #877로 수정(Draft, 병합은 승인 필요). 호스트 출력에는 영향이 없다.

## 10. 배포 준비 상태 (2026-10-23)

| 항목 | 상태 |
|---|---|
| Flyway | 운영 V1093·실패 0 확인(run 39875278441). 배포 시 V1094·V1095는 CREATE TABLE만, 자동 적용(§2) |
| 운영 JAR SHA | **바이트 대조 미완료.** `app.jar` SHA-256 `177f6222…9dd8`는 확보했지만 Deploy run 39733429342가 jar 해시를 기록하지 않았다. 시각 일치로 `2bed3918`로 판단한 상태다. 다음 배포부터는 배포 스크립트가 `app.jar.meta`(git_sha·jar_sha256)를 남기도록 하는 별도 PR이 필요하다(진단 문서는 그 파일을 읽을 준비가 되어 있다) |
| 운영 DB 보호 | 백업 7일·삭제 보호 승인됨, 대표님 CloudShell 실행 대기(`RDS_PROTECTION_CLOUDSHELL_GUIDE_20261023.md`) |
| 배포 직전 수동 스냅샷 | 절차 준비(아래), **미실행** |
| 536 dry-run | 준비 완료: 원본 536/316/SHA 536 검증, 현재 운영 SHA는 접속 전 거부(§7) |

### 배포 직전 수동 스냅샷 절차 (CloudShell, 별도 승인 후)
```bash
export AWS_DEFAULT_REGION=ap-northeast-2
DB=f2r-db-202610181331
SHA8=<배포할 main 커밋 앞 8자리>
SNAP="f2r-predeploy-$(date -u +%Y%m%d%H%M)-$SHA8"
# 조회: available·Pending {} 확인
aws rds describe-db-instances --db-instance-identifier $DB --query 'DBInstances[0].{Status:DBInstanceStatus,Pending:PendingModifiedValues}'
# 생성 (승인 후)
aws rds create-db-snapshot --db-instance-identifier $DB --db-snapshot-identifier "$SNAP" --query 'DBSnapshot.Status'
# 검증
aws rds wait db-snapshot-available --db-snapshot-identifier "$SNAP" && echo "$SNAP available"
```
- 단일 AZ라 스냅샷을 시작할 때 I/O가 잠깐 멈출 수 있다(보통 수 초). 저트래픽 시간에 하고, 배포 바로 전에 한다.
- 이름 형식은 #876의 `--confirm-snapshot` 검증(같은 SHA8, 24시간 이내)과 맞춘다.

## 11. 배포 직전 최종 확인 (2026-10-23 00:37 UTC)

| 조건 | 결과 |
|---|---|
| 운영 DB 보호 | 대표님 CloudShell 결과 + `Prod read-only status` [run 39879110307](https://github.com/woori73/feel2real-buildit/actions/runs/39879110307): `f2r-db-202610181331` 보존 **7일**, 삭제 보호 **True**, available. 다른 두 DB는 7일·False로 변경 없음 |
| Flyway | 같은 실행에서 188건, 최대 V1093, 실패 0, studio/mcp 테이블 0(변화 없음) |
| 최신 main | `5f48ab9b`(#878 병합) Build Check [run 39878445940](https://github.com/woori73/feel2real-buildit/actions/runs/39878445940) PASS |
| #878 | 병합됨(a1-precheck 로그의 DB 엔드포인트 해시 제거, 앱 코드 변경 없음) |
| 이전 JAR Flyway 롤백 | **PASS** — 아래 |

### 이전 JAR(2bed3918) 롤백 호환성 실측
- 방법: 운영과 같은 Flyway 11.14.1(두 SHA 모두 Spring Boot 4.0.6)로, prd 설정(`baselineOnMigrate`·`validateOnMigrate`, 그 밖은 기본값)과 `FlywayRepairConfig`(repair → migrate)를 재현했다. 격리 MySQL 8.0.46에서 실행했다(운영은 8.4.8).
- 증거: `handoff/probes/FlywayRollbackProbe.java.txt`, `FlywayRollbackProbe.result.txt`

| 단계 | 결과 |
|---|---|
| 1. 2bed3918 마이그레이션으로 빈 DB | 188건, V1093 |
| 2. main 배포 | V1094·V1095 적용(새 테이블 16개) |
| 3. 이전 JAR로 복구 기동 | **성공**, V1094·V1095는 `FUTURE_SUCCESS`로 무시되고 검증 통과 |
| 4. 다시 main | 성공, 추가 실행 0 |
| 5. V1095 실패 행이 남은 상태에서 이전 JAR | 성공(repair가 실패 행 제거) |
| 6. 남은 테이블 정리 없이 main 재배포 | **실패(예상)** — V1095 `CREATE TABLE` 중복 |

→ 결론: 배포 후 이전 JAR로 돌아가는 것은 DB 조치 없이 가능하다. 단, V1095가 **중간에 실패한** 경우에만 재배포 전에 남은 테이블을 정리하거나 스냅샷을 복원해야 한다(§2와 동일).
- 정정: §2의 "mcp_* 8개"는 7개다(합계 16개).
