# #853 · #854 · #855 단계별 배포 준비 — Claude 검증 보고서 (2차)

작성: 2026-10-22 (Claude). 역할: **검수·테스트 검증·운영 위험 평가**(구현은 Codex). 이번 단계에서 Claude는 어떤 PR 브랜치에도 push하지 않았다.
이전 보고서: [최종 검수](PR854_PR855_FINAL_REVIEW.md) · [#853 노트](PR853_NOTES.md)

## 0. 결론 먼저

| 항목 | 상태 |
|---|---|
| **#853 수집 배치 승인 우회 (최우선)** | **미수정 — 최신 헤드에서 재현됨.** 병합 차단 사유 |
| 병합 계획·Flyway·롤백 | 실제 MySQL 8.0.46에서 검증 완료 (§3) |
| 운영 RDS 호환성 | **버전 미확인.** 저장소 근거와 추론은 있으나 운영 DB 조회는 하지 않았음 (§4) |
| ChatGPT·Claude 실접속 | **미실행.** 공개 HTTPS와 사용자 계정 필요 (§5) |
| 실제 68컷 자료 검증 | **미실행.** 실제 EP01 파일 없음. 합성 22/22 통과 (§6) |
| #854 CI | 최신 헤드 `mysql-integration-test` 1회 실패(Selenium 시간초과 1건), 재실행 진행 중 (§1) |
| **Draft 해제·병합 승인 요청** | **아직 요청하지 않음.** 필수 검증 미완료 (§9) |

## 1. PR별 현황 (조회 시점 06:30 UTC)

| PR | 최신 HEAD | CI |
|---|---|---|
| #854 | `253d938` | build ✅ · mysql-integration-test: 1차 ❌ → 재실행 진행 중. 실패 1건은 `AdmissionsBrowserQaMySqlTest.memberDashboardPcAndMobileWithSourceAndHistory`(입시상담 화면 Selenium 요소 대기 시간초과, 360건 중 1건). main(`2bed391`)은 성공, #855(#854 포함)의 같은 잡은 성공했으므로 일시적 가능성이 높다 — **재실행 결과로 확정 필요** |
| #853 | `cbf19cd` | build ✅ · mysql-integration-test ✅ · sample-only ✅ |
| #855 | `2d7f74a` | build ✅ · mysql-integration-test ✅ · isolated ✅ · sample-only ✅ |
| main | `2bed391` | 성공 (마이그레이션 최고 번호 여전히 V1093) |

## 2. 최우선 결함: #853 수집 배치의 승인 없는 확정

**판정: 열려 있음 (재현 확정).**

- 원인: 공용 `EasyImportService.confirm(batchId, rowIds)`가 배치 출처·소유자·승인 상태를 전혀 보지 않는다. 같은 메서드를 승인 서비스(`EasyCollectionApprovalService.commitLocked`)도 쓰기 때문에 서비스 입구에서 구분되지 않는다. 사용자 입력 `batchId`로 도달하는 입구: `EasyImportController`(관리자), `EasyTutorImportController`→`EasyTutorImportService`(강사), `EasyAgentService:136`, (#854) `UnifiedAssistantService` LEDGER_IMPORT.
- 최신 헤드의 `EasyImportService.java`는 #853 `60479f1` 이후 **변경이 없다.** Codex의 "금융배치 보호 가드" 커밋은 #854의 `LEDGER_ACCOUNT` 가드(Claude 작성)이며 #853 확정 경로와 무관하다.
- **재현 테스트** (실제 MySQL 8.0.46, 최신 #855 `2d7f74a`): `EasyCollectionConfirmBypassMySqlTest` 2건 **모두 실패** — 승인 요청 없이 `ingestFinancialDraft`로 만든 FINANCIAL_FILE 배치를 `importService.confirm()`과 강사 파사드로 확정하면 예외 없이 장부에 거래가 생긴다. (수집 초안 생성·`importBatchId` 연결 단언은 통과 → 환경 오류 아님)
- 전달물: `docs/reviews/handoff/EasyCollectionConfirmBypassMySqlTest.java.txt` (`src/test/java/.../domain/easy/`에 `.java`로 복사하면 컴파일됨).

**Codex 수용 기준**
1. 서버측 가드: `EasyCollectionRun.importBatchId`로 연결된 배치(또는 adapter `FINANCIAL_FILE`)는 일반 `confirm`/`selectAccount`/`prepare`에서 거부. 승인 서비스만 쓰는 별도 내부 메서드(예: `confirmApproved`)로 분리하고 승인 상태(APPROVED/COMMIT_FAILED), 사업체 소유, reviewHash 일치를 그 안에서 재검증.
2. 위 재현 테스트 2건 통과 + 기존 `approveAndCommit` 경로(`EasyCollectionApprovalCommitMySqlTest` 등)와 #854 일반 CSV 업로드→승인 흐름(비서가 직접 만든 배치)은 계속 통과.
3. `EasyAgentService`, `EasyAuditService`(305·340줄)가 수집 연결 배치에 닿지 않음을 테스트 또는 근거로 제시.

## 3. 의존성·병합 계획 검증

**실제 Git 관계 (측정값)**
- `#854(2f7d0db 이후 253d938)` ⊂ `#855`: **예**(#855에 없는 #854 단독 커밋 0개).
- `#853`: #855에 없는 커밋은 병합 커밋 1개뿐. #855 트리는 #853 변경 96개 파일 중 문서·SecurityConfig·비서 3개 파일(당시 #854 최신 커밋 차이)만 다름 → 사실상 #853 포함.
- #853은 #854의 마지막 커밋을 늦게 따라온다(이번 확인 시점에 Codex가 동기화 중).
- main 대비 앞선 커밋: #854 12, #853 17, #855 33. 공통 베이스 `7472d91`.

**새 main(`2bed391`) 기준 순차 병합 시뮬레이션 (스크래치 브랜치, 직접 실행)**
1. #854 병합: `build.gradle` 1곳 충돌(main이 `maxHeapSize` 2048m로 변경, Codex가 인접 줄 추가) — **두 변경을 모두 유지하면 해결**. (Codex가 `253d938`에서 이미 반영)
2. #853 병합: 충돌 없음. 3. #855 병합: 충돌 없음. 최종 트리 = #855 헤드 + main 신규 15개 파일.
4. 마이그레이션: main V1093 → #854 **V1094** → #855 **V1095**, 중복·역행 없음.

**Flyway 실측 (MySQL 8.0.46)**
- main(193건, V1093) 기동 → 통합 트리 기동: **V1094, V1095 두 건만 적용, Hibernate validate 통과, 기동 성공.**
- 역순(V1095 먼저) 배포: V1094가 미적용 상태로 남아 **앱 기동 실패**(이전 단계 시험). → V1094 이전에 V1095 배포 금지.
- 통합 트리 테스트(Studio/MCP/비서/이지장부 수집/위젯/보안): **295건 통과, 실패 0**.

**권장 병합 계획**
| 단계 | 내용 | 배포 후 확인 |
|---|---|---|
| 사전 | Codex가 2번(§2) 수정 완료, 세 PR 최신 main 병합·CI 전부 성공, Claude 재검증. 운영 RDS 수동 스냅샷 | — |
| 1 | #854 병합(V1094) | 기동·Flyway validate, 비서 쓰기 OFF 확인 |
| 2 | #853 병합(마이그레이션 없음) | 기동, 수집 기능 로컬 한정 유지 |
| 3 | #855 병합(V1095) — 1·2가 main에 있으면 diff는 #855 고유분 | 기동, `app.mcp.enabled=false` 확인 |
| 대안 | #855 한 번에 병합(세 PR 내용 포함, 한 번의 배포) — 최종 트리는 동일. 리뷰·롤백 단위가 커지므로 비권장 | |

**롤백 절차 (검증된 부분 표시)**
1. **앱:** 이전 JAR로 되돌린다(기존 `f2r_app_guard` 롤백 사용). ✅ *V1095까지 올라간 DB에서 main 버전 앱이 정상 기동함을 실측(경고 "스키마 버전이 가장 최신 마이그레이션보다 높음"만 출력).*
2. **DB:** Flyway는 down 마이그레이션이 없다. V1094/V1095는 **신규 테이블만 추가**(`unified_assistant_*`, `studio_*`, `mcp_*`)라 두어도 구버전 앱에 영향 없음. 테이블 삭제는 데이터 폐기이므로 **별도 승인 + 스냅샷 후**에만.
3. **기능 차단:** `buildit.unified-assistant.write-enabled`, `model-analysis-enabled`, `app.mcp.enabled`는 운영에서 기본 OFF(prd 설정에 키 없음 → 코드 기본값 false). ✅
4. `outOfOrder`/`baseline` 조작으로 순서 문제를 덮지 않는다.

## 4. 운영 RDS 호환성

운영 DB에는 접속하지 않았다(금지 사항). 저장소 근거만 정리한다.

| 확인 항목 | 근거 | 결과 |
|---|---|---|
| 엔진 종류 | `migration-infra-build.yml`(`--engine mysql`, "RDS MySQL" 보안그룹), `AWS_DEPLOYMENT.md` | **RDS for MySQL**(Aurora·MariaDB 아님) |
| 정확한 버전 | 워크플로우에 "구 RDS 엔진 버전과 같아야 함 — 구 RDS 확인 후 선택"(기본 `mysql8.4`, 선택지 `mysql8.0`)만 있고 **운영 값은 저장소에 없음** | **미확인** |
| 하한 추론 | 기존 `V972`가 `DROP CHECK`(MySQL ≥ 8.0.16 문법) → 운영은 이미 8.0.16 이상으로 보임 | 추론 |
| CI 검증 엔진 | 전체 체인 `mysql:8.0`(build-check), Studio/MCP 격리는 `mysql:8.4`, 로컬 compose는 8.4; Claude 실측 **8.0.46** | 8.0·8.4 모두 통과 |
| V1094·V1095 사용 기능 | InnoDB 일반 테이블, `users(user_id)` FK, `MEDIUMTEXT/TEXT`, `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`, 복합 PK 길이 656바이트(< 3072) — 8.0/8.4 특유 구문 없음 | 호환 예상 |
| 문자셋 | V1095는 테이블 문자셋 미지정 → DB 기본값 상속 | 운영 DB 기본 `utf8mb4` 확인 필요 |
| 인프라 상황 | 신규 AWS 계정으로 RDS 스냅샷 복원·DNS 전환 작업이 진행/예정(`restore-rds`) | **전환 작업과 배포 시점이 겹치지 않게 조율 필요** |

**사용자 확인 요청(읽기 전용, 운영 관리자 직접 실행):**
`SELECT VERSION(), @@character_set_server, @@collation_server, @@sql_mode, @@lower_case_table_names, @@max_connections;`
또는 `aws rds describe-db-instances --query 'DBInstances[].{Engine:Engine,Ver:EngineVersion,Family:DBParameterGroups}'`. 결과가 MySQL 8.0.16+ / 8.4이면 호환성 "예상"이 "확인"이 된다.
(참고: `prod-diagnostic.yml`에 읽기 전용 진단 단계가 있으나 운영 접속이므로 별도 승인 전 실행하지 않았다.)

## 5. MCP 운영 준비

| 항목 | 상태 |
|---|---|
| 읽기 전용 정책 | ✅ 허용목록 406건, GET 전용, 쓰기·외부호출 GET 제거. 통합 트리 테스트 통과 |
| 사용자·학원·역할 접근 통제 | ✅ 기존 권한 재사용 + OAuth scope. HTTP 시험(위조·무토큰 401, 권한 회수 후 중단). **AcademyCraft 실데이터·타 학원 대조는 미검증** |
| 관리자 민감 동의 | ✅ 경고 문구·체크 없이는 400, 체크 시 303(HTTP 시험 통과) |
| 개인정보 마스킹·감사 로그 | ✅ 전화/이메일/주민/계좌 형태 마스킹(단위 5건), `mcp_audit`·`oauth_consent` 기록 |
| 사용자별 동시 2건 | ✅ 단위 시험 |
| **프록시 뒤 속도 제한** | ⚠️ **운영 전 필수 설정:** `app.admissions.rate-limit.trusted-proxies`의 기본값이 빈 값이라 ALB 뒤에서는 익명 MCP 엔드포인트(등록·토큰)가 **모든 사용자 합산(ALB 주소 하나)** 으로 제한된다. MCP를 켜기 전에 ALB 대역을 설정해야 한다(Codex: 값이 비어 있으면 시작 시 경고/차단 권장) |
| 공식 SDK 시험 | ✅ 독립 OAuth 클라이언트 2개 동일 결과, 쓰기 거부 |
| **공개 HTTPS ChatGPT·Claude 실접속** | ❌ **미실행.** 이 환경에는 공개 주소·두 제품 계정 세션이 없다. 성공으로 보고하지 않는다 |

실접속 시험 전제(사용자 조작): ① 격리 테스트 서버 공개 HTTPS ② `app.mcp.enabled=true`, `public-base-url`, `redirect-uris`(ChatGPT 앱 관리 화면의 정확한 콜백 포함) ③ 두 제품에서 커넥터 추가 → 본인 로그인·동의 ④ `docs/ai-agent-studio/CONNECTION_TEST.md`로 같은 파일 ID·원본 SHA를 두 제품과 스튜디오에서 대조.

## 6. 제작 스튜디오 (PC·모바일)

- 통합 트리 JAR을 기동해 `browser-qa.cjs`를 **직접 재실행: 22/22 통과** (PC 1440, 모바일 390px — 68컷 표시, 모바일 승인·저장 재조회, 모바일 복원은 미승인 새 버전/기존 승인 유지, 7종 원본 업로드 SHA-256 일치, 중복 방지, HTML 스크립트 차단, axe 위반 0, JS 오류 0, 미허가 403). 결과: `docs/reviews/handoff/browser-results-claude-rerun.json`.
- **모든 자료는 합성이다.** 실제 EP01 68컷 자료는 제공되지 않아 "실제 68컷 등록·SHA·승인 포인터·복원" 확인은 **미실행**(형식·라벨은 `R14_R8_READINESS.md`).
- 영상 연결·대용량 업로드는 요청대로 후속 분리.

## 7. AI 비서 운영 준비

- 쓰기 기능: 기본 OFF, local만 ON, 모든 쓰기는 사용자 확인(승인 해시) 초안 방식. 허용 업무 목록(학생·반·배정·출결 기록·상담·장부 입력/가져오기/계정)만. 출결 정정은 차단. ✅
- Muse 한도: 사용자별 분당 6/일 60/자료당 3(설정값), 초과 시 규칙 분석 폴백. ⚠️ **인스턴스 단위·사용자 단위뿐**이라 조직 전체 일일 호출·비용 상한이 없다 → Codex: 전역 일일 상한과 즉시 차단 스위치 추가 권장. 실제 Muse 응답은 여전히 미검증.
- 입시상담·이지장부 조회 범위 확대 제안(구현 아님): 현재 입시상담 1경로(본인 이력), 이지장부 1경로(월 합계 8필드). 확대 후보는 ① 이지장부 거래 목록 중 사업체 허용 + 마스킹 적용 읽기 전용 경로, ② 입시상담 관리자 통계. 각각 **별도 scope + 관리자 동의 + 최소 필드 + 서버 권한 시험**을 통과해야 하며 사용자 결정 전에는 늘리지 않는다.

## 8. 잔여 결함

- **P0:** 없음 (수정 전 상태에서 개인정보 노출은 없으나, §2는 승인 우회라 P1로 분류).
- **P1:** (1) #853 수집 배치 승인 우회(§2) (2) MCP 운영 활성화 전 trusted-proxies 미설정(§5) (3) 실제 ChatGPT·Claude 접속 미검증 (4) #854 CI 재실행 결과 대기.
- **P2:** 운영 RDS 버전 미확인 · 실제 68컷 자료 미검증 · Muse 조직 전체 상한 없음 · 실제 Muse 응답 미검증 · AcademyCraft 실데이터 격리 미검증 · 출결 정정 미제공 · 승인 전 import 배치 고아 정리 정책 없음 · #854 Playwright E2E(Docker) 이번에 미실행 · 원본 100MiB byte[] 적재 · 이전 V1094 적용 로컬 DB 체크섬 불일치.
- **P3:** 마스킹 휴리스틱 · S3 읽기 비용 · 기존 JS 테스트 40건 실패(main과 동일).

## 9. 승인 요청 상태

모든 필수 검증이 끝나지 않았으므로 **Draft 해제와 병합 승인은 지금 요청하지 않는다.** 남은 필수 조건:
1. Codex: §2 수정 + 세 PR 최신 CI 전부 성공(#854 재실행 포함).
2. Claude: §2 수정 재검증(재현 테스트 + 승인 경로 회귀).
3. 사용자: 운영 RDS 버전 확인(§4), 공개 HTTPS 실접속 시험(§5), 실제 68컷 파일 제공(§6), 병합 순서·배포 시점 승인.
위가 충족되면 Claude가 최종 재검증 후 단계 1(#854)의 Draft 해제·병합 승인을 요청한다. 운영 DB 변경·운영 배포는 별도 승인 전까지 하지 않는다.
