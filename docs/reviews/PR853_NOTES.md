# PR #853 검토 노트 (읽기 전용, 코드 수정 없음)

대상: woori73/feel2real-buildit #853 `feat/financial-data-collector` (head f4ab861, base main 7472d91). 상태: draft, mergeable_state=unstable, 92 files / +4072 -140.
파일 경로는 `src/main/java/f2r/tutor_solution/private_tutor_solution_back/` 를 `J/` 로 줄여 쓴다.

## 0. 결론 요약
- P0 없음. 인증정보/쿠키/OTP 저장·로그 노출은 설계상 없음 (아래 A-1). 실기관 접속은 loopback mock 으로 코드 차단.
- **P1 (병합 순서/범위)**: #853 head 가 #854(통합 AI 비서) 전체를 포함한다(`git diff origin/feat/unified-ai-assistant pr853` 에는 853 고유 파일만 남음). PR 본문은 "작업 B"만 설명하지만 diff 에 `V1094__unified_assistant.sql`, `domain/unifiedassistant/*`, dashboard.html 등 #854 내용이 들어 있다. 또 #855 도 `V1094__studio_and_mcp.sql` 이라 **세 PR 모두 V1094 를 점유**한다.
- **P1 (CI)**: `mysql-integration-test` 실패(run 39726386419, job 119651131845). build / sample-only 는 성공. 로그 tail 로는 원인 확정 불가(Gradle 실패, 실제 MySQL 테스트 단계). 원인이 #853 인지 #854 편입분/기존 main 인지 미확인 → 병합 전 확인 필요.
- **P2 (승인 우회, 기존 구조 유래)**: 853 이 만든 import batch 는 기존 `POST /api/admin/easy/imports/{id}/confirm`, `POST /api/tutor/easy/.../imports/{id}/confirm` 로도 확정 가능 (아래 B-2). 853 의 reviewHash/승인 래퍼는 "정상 경로의 강제"일 뿐 "유일 경로"가 아니다.

## A. #853 자체

### A-1. 인증정보·쿠키·OTP (P1 점검 → 결함 없음)
- 서버 DTO 에 비밀번호/쿠키/OTP/인증서/URL/셀렉터 입력 없음: `J/domain/easy/dto/EasyFinancialCollectorDtos.java:5` (주석), 컨트롤러 파라미터 `EasyFinancialCollectorController.java:46-51`.
- Playwright 수집기는 사람이 직접 인증, 프로세스는 자격증명 미접촉: `local-collector/src/collector.js:1-5, 42-68`. 매 작업 새 `newContext`, `finally` 에서 close (`collector.js:30, 86-89`), storageState/persistent profile 없음 (README "Real institutions" 절).
- 로그: 실패 시 상세 메시지 폐기 `collector.js:124-125` (고정 문자열), 파서 예외 메시지 미노출 `EasyFinancialCollectorService.java:53-58`, vault 감사에 원문/파일명/예외 없음 `EasyFinancialVault.java:86-90`.
- 관찰(P3): `uploadPreview.js:30` 는 관리자 JWT(`EASY_PREVIEW_ACCESS_TOKEN`)를 env 로 받아 Cookie 헤더로 전송. loopback http 만 허용(`downloadSafety.js:5-12`)하고 `redirect:'error'` 라 유출 위험 낮음. 다만 `http://` 평문 + 장기 JWT 를 env/셸 이력에 두는 운영 관행은 문서에 경고 필요.
- 관찰(P3): `EasyCollectionApprovalService.java:~205` 에서 `e.getMessage()` 를 `markCommitFailedTx` 에 저장 — 853 이 의도한 것은 아니나 행 내용이 메시지에 섞일 가능성은 낮지만 검증 안 됨.

### A-2. 사업체 격리 (결함 없음)
- 모든 서비스 메서드 첫 줄 `access.requireAllowedBusiness(principal, businessId)`: `EasyFinancialCollectorService.java:37, 68, 75, 109`.
- vault 객체가 businessId+UUID+object 를 파일명과 GCM AAD 에 결합하고 복호 후 scope 재검증: `EasyFinancialVault.java:119, 126, 77-79`. 타 사업체 파일 복사 시 인증 실패.
- status 조회가 batch.businessId 불일치 시 SecurityException: `EasyFinancialCollectorService.java:145-147`.
- 컨트롤러 `@PreAuthorize("hasRole('ADMIN')")` (`EasyFinancialCollectorController.java:26`) + 기존 `/api/admin/easy/**` ADMIN 규칙.
- P3: `status()` 가 `ingest.listRuns(businessId)` 전체를 스트림 필터링 (`:139-142`) → 사업체 run 이 많으면 느려짐. 사업체 내부 문제일 뿐 격리 문제 아님.

### A-3. 승인 없는 장부 반영 (853 내부 경로는 안전, 단 아래 P2)
- `upload` / `stage` 는 confirm 미호출 (`EasyFinancialCollectorService.java:35-104`). `approveAndCommit` 만 장부 반영, `userApproved=true` + reviewHash 일치 필요 (`:107-120`).
- 업로드에도 별도 동의 플래그 `userApprovedUpload` (`:39`, 컨트롤러 `:50`).
- BANK_ACCOUNT 만 stage/commit (`:78-80, 112-114`); 카드·세무 증빙은 거부.
- **P2**: `approveAndCommit` 가 한 요청 안에서 `requestApproval` → `approveAndCommit` 을 같은 사용자로 연속 호출 (`:122-126`). 4-eyes 가 아니라 "한 관리자의 2단계 클릭". PR 본문/문서가 "명시 승인"이라 했으므로 허용 가능하나, 승인자=요청자 분리는 아님을 문서화 필요.
- **P2**: reviewHash 는 GET status 의 batch JSON 해시이며(`:150-156`) 검증 후 `commitLocked`(`EasyCollectionApprovalService`) 가 `confirm(batchId, null)` 로 **모든 행** 확정. hash 검증과 commit 사이 다른 경로(B-2)가 batch 를 바꿀 수 있는 짧은 TOCTOU 창이 있음. PR 문서도 "CAS 없음" 으로 인정 (SECURITY 문서 "남은 제한").
- P3: `stage`/`approveAndCommit` 의 `synchronized` 는 단일 JVM 한정 (`:73, 107`). 문서가 인정.

### A-4. Playwright 수집 안전성
- 도메인 제한: `mockOrigin()` 이 `http://localhost|127.0.0.1:<port>` 아니면 throw (`downloadSafety.js:5-12`), `pollAndRun` 시작 시 호출(`collector.js:104`), `context.route('**/*')` 로 타 origin abort + WebSocket close + serviceWorkers block (`collector.js:30-34`). 실기관 workflow 를 추가해도 엔진이 거부. 좋음.
- 약관: 실기관 접속 비활성, README/INSTITUTION 문서에 별도 승인 필요 명시. 약관 위반 리스크는 현 코드에서 없음.
- 다운로드: 확장자 화이트리스트 + 10MB 제한 + AES-256-GCM + UUID 파일명 + 0600/0700 + symlink 검사 (`downloadSafety.js:21-52`). 키는 env `EASY_COLLECTOR_FILE_KEY_BASE64`.
- P3: AAD 가 `filename` 문자열만(`downloadSafety.js:44`) → 같은 키로 암호화된 다른 파일명으로 이름을 바꿔 복사하면 복호 실패(좋음). 단 keyBase64 를 서버 vault 키와 같은 값으로 쓰면 키 재사용이므로 별도 키 권장 문서 필요.
- P3: `collector.js:123` headed `chromium.launch` — CI 에서는 xvfb. 문제 아님.
- P2: `easy-collection-worker/node_modules/**` 161개 파일이 main 에 이미 커밋됨(기존 이슈, 853 무관). 언급만.
- P2: 수집기 코드 중 `testHooks.onCollectionConsent` / `onWaitingForAuth` 가 production 에서 주입 가능하지는 않으나(모듈 export `pollAndRun`) 동일 프로세스 코드에서 호출하면 동의를 우회할 수 있음. 테스트 전용임을 코드 주석이 이미 설명. (P3로 낮춰도 됨)

### A-5. 서버 파서/업로드 안전성
- `EasyFinancialFileParser`/`EasyFinancialReportBuilder` 는 `@Profile`/`@ConditionalOnProperty` 가 **없는** `@Service` (`EasyFinancialFileParser.java:23`, `EasyFinancialReportBuilder.java:14`) → prd 에도 빈 생성. 호출 경로(컨트롤러/서비스)는 local 한정이라 도달 불가지만 POI-ooxml/PDFBox 가 prd 클래스패스에 추가됨(build.gradle `poi 5.5.1`, `poi-ooxml 5.5.1`). 의존성 확대 = 공격면 증가는 아니나 의도와 불일치 → P3.
- POI/PDFBox 를 앱 프로세스 안에서 구동(문서가 인정). 압축 폭탄/CPU 는 크기·행·열·셀·페이지 상한만으로 방어 (`:26, 61-63, 78, 89-93`). 로컬 한정이면 수용 가능, prd 활성화 전 격리 필수.
- P3: `LocalDate.parse(scope.from())` (`EasyFinancialReportBuilder.java:22-23`) 의 DateTimeParseException 이 500 이 될 수 있음(핸들러 미확인).
- P3: `Report.drafts` 에 counterparty/description 포함해 API 응답으로 반환 (`EasyFinancialCollectorDtos.java:28-33`). ADMIN 한정이나 "never returned by API" 주석 (`:35`) 은 원본·rows 에만 해당. 응답 `no-store` 헤더는 이 컨트롤러에 없음(#854 는 있음) → P3.

### A-6. 마이그레이션 번호
- #853 고유 마이그레이션은 **없음** (V 파일 신규 0, 신규 테이블 0). 그러나 head 에 `db/migration/V1094__unified_assistant.sql` 이 #854 에서 편입되어 있다.
- main 최신 V1093 (`V1093__wiki_exam_rounds_and_original_year_month.sql`). 현재 상태: #853 head(=#854 포함) V1094 unified_assistant, #855 V1094 studio_and_mcp. → 사용자가 말한 계획(#854=V1094, #855=V1095)대로라면 **#855 의 `V1094__studio_and_mcp.sql` 를 V1095 로 개명**해야 하고, #853 은 마이그레이션을 가지지 않도록 #854 분리(리베이스) 필요. WORKFLOW.md §4 "운영 위치 버전 중복 금지(prd 502 원인)" 위반 위험.
- 적용된 V 파일 불변 원칙: #854 가 먼저 prd 에 적용되면 V1094 내용 변경 불가.

### A-7. 문서 충돌
- `docs/API.md`, `DOMAIN.md`, `FLOWS.md`, `INDEX.md` 모두 "작업 B"와 "작업 A(#854)" 문단이 같은 파일에 추가됨. #854 단독 병합 후 #853 을 병합하면 동일 위치(파일 말미/동일 절) 충돌 가능성이 높음. 또한 `DOMAIN.md` 는 #853 자체의 신규 테이블이 없다는 점을 언급하지 않고 #854 테이블만 기술.
- 문서 정합성: API.md 의 "통합 AI 자율 도구에 stage/commit 을 노출하지 않는다" 는 문장은 #854/#855 코드와 일치(B-1).
- 보안 문서의 "Java 99건" 은 build-check.yml 의 기본 CI 에는 포함되지 않음: `build-check.yml` 에 financial 테스트 추가가 없고 path 필터 워크플로 `financial-collector-check.yml`(sample-only, 성공) 로만 실행됨. 해당 워크플로는 `src/**/easy/**` 등 변경 시에만 동작 → 이후 다른 PR 의 회귀 감시 약함 (P3).

### A-8. CI 상태 (head f4ab861)
| 체크 | 결과 |
|---|---|
| build | success |
| sample-only (financial-collector-check) | success |
| mysql-integration-test | **failure** (실 MySQL 8.0 서비스 컨테이너, Gradle BUILD FAILED 4m21s) |
PR 본문도 "실 MySQL 락/트랜잭션 테스트는 수행하지 않음" 이라 했으므로 실패 원인이 853 변경(ingestFinancialDraft, poi 5.5.1, 편입된 #854 V1094)과 관련 있는지 별도 확인 필요. (이 노트에서는 원인 로그 확정 못 함.)

## B. 연동 위험 (#854, #855 인터페이스 관점)

### B-1. 무단 조회 (MCP 정책) → 직접 노출 없음, 간접 노출 있음
- #855 정책은 정확 경로 허용목록. `src/main/resources/mcp/read-policy.json` 에 `/api/admin/easy/financial-collector/*` 항목 **없음**, `/api/dashboard/assistant/v1/*` 항목도 없음 (grep 0건). `McpCatalog.java:14` 주석 "runtime mappings are authoritative; a committed exact-path policy is required for execution" → 기본 제외. 또한 853 컨트롤러는 `local & !prd` 라 prd 런타임 매핑에도 없음. `McpGateway.read` 는 `.GET()` + 내부 loopback 호출만 (`McpGateway.java:~212-240`) 이므로 POST(previews/stage/approve-and-commit)는 구조적으로 호출 불가.
- **간접 노출 (P2)**: 정책에 `GET /api/admin/easy/imports`, `/api/admin/easy/imports/{batchId}`, `/transactions`, `/accounting/*`, `/api/tutor/easy/accounting/journal` 등이 허용됨(read-policy.json 의 easy 항목). 853 의 `stage` 는 같은 import batch 테이블에 거래처/적요가 담긴 초안 행을 쌓으므로, ADMIN 권한 토큰의 MCP 클라이언트는 **승인 전 금융 초안 batch 를 읽을 수 있다**(다만 MCP 로 사용자가 이미 허용한 `easy` scope + ADMIN 권한 내부이며 `redact()` 적용). 의도 여부를 정책 `reason` 에 명시하거나 `imports*` 는 txn 확정분만 보이게 제한할지 결정 필요.
- #854 `GET /api/dashboard/assistant/v1/drafts|audit|overview` 는 정책 미포함 → MCP 로 초안/감사 조회 불가. 단 `/api/dashboard/businesses` 는 포함.
- 정책 변경 시 체크리스트: financial-collector `GET /previews/{id}` 를 추가하면 Status 에 batch 전체와 reviewHash 가 담겨 reviewHash 유출 → 추가 금지 권고.

### B-2. 승인 없이 장부 반영되는 경로
- #853 → 장부: `approveAndCommit` 만 (정상). #855 MCP: 읽기 전용 (`McpReadOnlyTransactionFilter` + GET only) → 반영 경로 없음.
- **#854 → 장부 (P2)**: `UnifiedAssistantService.decide` 의 `LEDGER_IMPORT` 는 `imports.confirm(p, businessId, batchId, readyRowIds)` 호출 (`UnifiedAssistantService.java:138-141`) = `EasyTutorImportService.confirm` → `importService.confirm` (`EasyTutorImportService.java:98-103`). 이 경로는 자체 초안(`uploadCsv` 로 생성한 batch)에 대해서만 쓰이지만, `LEDGER_ACCOUNT` 초안은 **임의의 batchId(같은 사업체)** 를 받는다(`UnifiedAssistantService.java:101-104`, `requireOwnedBatch` 는 사업체 소속만 검사, `EasyTutorImportService.java:113-118`). 따라서:
  1. #853 이 stage 한 FINANCIAL_FILE batch 의 행 계정을 #854 승인(`selectAccount`, `:154`)으로 바꿀 수 있음 → 853 reviewHash(batch 해시) 가 변해 `REVIEW_CHANGED_OR_NO_BATCH` 로 막히는 것은 안전 쪽 동작이지만, 반대로 hash 검증 직후~commit 사이 변경 가능(A-3 TOCTOU).
  2. 기존 `EasyImportController:59-64`, `EasyTutorImportController:77-82` confirm 은 batch 의 출처(run/approval)를 보지 않음 → FINANCIAL_FILE batch 도 collection approval 없이 확정 가능 (기존 구조 한계; `EasyCollectionController.java:31` 주석이 "confirm 은 imports confirm 만 유효" 라고 이미 말함). 853 이 새로 만든 갭은 아니나 "approve-and-commit 이 유일 경로" 라는 문서 주장(API.md "명시 반영")은 과장.
  3. `EasyAgentService.java:136` 도 batch confirm 사용 (기존).
  권고: collection run 에 연결된 batch(`EasyCollectionRun.importBatchId` 존재)는 `EasyImportService.confirm` 입구에서 "approval COMMITTED/APPROVED 아니면 거부" 하는 서버측 가드, 또는 #854 `LEDGER_ACCOUNT/IMPORT` 에서 collection-linked batch 제외.
- #854 `write-enabled` 는 base 기본 false, `application-local.yaml` 만 true (diff 확인) → prd 에서 쓰기 503. 양호.
- 인터페이스 불일치: 853 은 `EasyImportService/EasyCollectionApprovalService`(admin 계열) 직접 사용, 854 는 `EasyTutorImportService`(tutor 파사드) 사용. 서로 다른 입구로 같은 `easy_import_batch` 를 변경.

### B-3. 같은 import 서비스 공유 시 중복 방지/배치 충돌
- 중복 방지 층: (a) 853 `ingestFinancialDraft` 는 `FINANCIAL_FILE` + `financial-<uuid>` 로 run 멱등 (`EasyCollectionIngestService.java` 신규 메서드); 외부키는 `FIN_<hash24>` scope 기반 (`EasyFinancialReportBuilder.java:84-92`); (b) 장부 수준 fingerprint 중복검사 `postRow()` (approval 서비스 주석 및 `EasyImportService.java:181, 276-287`)는 모든 경로 공통.
- #854 CSV 경로(`uploadCsv` → `importService.stage`)는 scope 해시/external id 없이 장부 fingerprint 로만 중복 판정. 같은 은행거래를 #853 으로 먼저 장부 반영한 뒤 #854 로 CSV 재업로드하면 fingerprint 로 DUPLICATE 처리될 것으로 보이나, **#853 staged(미확정) 상태와 #854 staged 가 동시 존재하면 양쪽 모두 READY → 먼저 확정한 쪽만 반영, 나머지는 확정 시 DUPLICATE** (fingerprint 일치 시). 계좌/기간 범위 보존키(FIN_ scope)는 #854 경로에 없어 "stage 단계 중복 경고"는 서로 못 봄. 필드 정규화 차이(counterparty/description 공백·빈 값; #854 `allowedCsv` 는 필드 일부만 사용)로 fingerprint 가 다르면 이중 반영 가능 (P2, 실 MySQL 로 검증 필요).
- 배치 충돌: #854 `LEDGER_ACCOUNT` / `LEDGER_IMPORT` 가 #853 batch 에 `selectAccount`/`confirm` 하면 853 의 `approval.run.importBatchId` 가 가리키는 batch 상태가 외부에서 변경됨. 853 은 batch 버전/CAS 없음(자체 인정). 동시 실행 시 `commitLocked` 는 승인 행 비관적 잠금만 걸고 import batch 자체는 잠그지 않음.
- 드래프트 카운터: #854 는 사용자당 PENDING 20개 제한; #853 stage 는 별도라 영향 없음.
- #855 는 import 서비스를 공유하지 않음(읽기 프록시) → 충돌 없음. 단 마이그레이션 번호 충돌(A-6).

## C. 권고 우선순위
1. (P1) #853 에서 #854 편입분 분리 또는 병합 순서 확정: #854 → #853(리베이스, V 파일 없음) → #855(V1095 개명). 문서 충돌 사전 정리.
2. (P1) mysql-integration-test 실패 원인 확인(재실행/main·#854 와 비교) 후 병합.
3. (P2) collection-linked import batch 의 직접 confirm/selectAccount 서버측 차단(또는 최소한 문서에 "유일 경로 아님" 명시).
4. (P2) MCP 정책의 `imports*` 노출 의도 확인; financial-collector 경로는 정책에 추가 금지.
5. (P3) 파서 빈 `@Profile` 부여, 컨트롤러 `Cache-Control: no-store`, 날짜 파싱 400 처리, 승인자/요청자 동일성 문서화.
