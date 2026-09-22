# EASY Visible Local Browser Collector — Home → Academy Handoff (2026-09-22)

> 집 PC(Home PC) 작업 종료 시점 인계 문서. 학원 PC(Academy PC)에서 이어서 작업함.
> 이번 문서가 다루는 범위: Phase 5A.2 "Visible Local Browser Collector V1" foundation.
> **REAL CARD COMPANY POC는 아직 시작하지 않았다.**

---

## 1. GITHUB STATE

**Repo:** woori73/feel2real-buildit
**Branch:** `feat/easy-visible-local-collector-v1`
**Commit:** `c5b84dd0f7d8c290ec84275d44920a9228e21246`
**PR:** [#369](https://github.com/woori73/feel2real-buildit/pull/369) — **OPEN** (MERGEABLE, CI 확인 필요 — §8)
**Base main SHA:** `1cd88f64c5cac935781472c1caeaa32620795c14` (origin/main, 분기 시점과 동일 — main에 새 커밋 없음)

이 문서 자체도 같은 브랜치(`feat/easy-visible-local-collector-v1`)에 커밋되어 PR #369에 포함된다.

---

## 2. ARCHITECTURE / STATE MACHINE

```
EASY(ROLE_ADMIN)                    Local Collector agent (학원 PC, Node.js)
─────────────────                   ──────────────────────────────────────
POST /collector/register  ──1회──▶  (토큰을 사람이 직접 복사해 env로 설정)
POST /collector/tasks     ──큐잉──▶  (poll)
                           ◀──GET /collector-agent/next-task──
                                     STARTING → BROWSER_OPEN (서버측 claim 시 자동 전이)
                                     visible Chromium 실행 → 로그인 페이지 이동
                           ◀──POST .../waiting-for-auth──
                                     [사람이 직접 인증 — Collector는 손대지 않음]
                           ◀──POST .../resume──            (AUTHENTICATED, 같은 세션 재개)
                           ◀──POST .../collecting──         (메뉴 이동 → 기간 입력 → 조회)
                           ◀──POST .../downloading──        (다운로드 트리거)
                           ◀──POST .../completed {filename,size,sha256}──   (COMPLETED)
```

**State enum** (`EasyCollectorTaskStatus`):
`STARTING → BROWSER_OPEN → WAITING_FOR_USER_AUTH → AUTHENTICATED → COLLECTING → DOWNLOADING → COMPLETED`
— 엄격한 순서만 허용(건너뛰기/역행 거부), `FAILED`/`CANCELLED`는 종료상태가 아닌 어느 시점에서든 가능.

**핵심 엔티티/서비스** (모두 신규, V991 migration):
- `easy_local_collectors` — business ↔ collector 페어링. `token_hash`(SHA-256)만 저장, 원문 토큰은 등록 응답에 1회만 노출.
- `easy_collector_tasks` — 위 state machine. 다운로드 metadata(filename/size/sha256)만 저장, 파일 원본 없음.
- `EasyLocalCollectorService` / `EasyCollectorTaskService` — business isolation(관리자쪽) + collector ownership isolation(에이전트쪽, 다른 collector의 task를 건드릴 수 없음) 양쪽 다 가드.
- `EasyCollectorCredentialGuard` — `completed`/`failed` payload에 비밀번호류 필드가 오면 조용히 버리지 않고 **명시적으로 거부**(400).

---

## 3. SECURITY / AUTH HANDOFF 방식

- **EASY는 어떤 시점에도 공동인증서·비밀번호·OTP·카드 CVC·카드 전체번호를 받거나 저장하지 않는다.** 코드 경로 자체에 그런 필드가 없다(구조적으로 불가능 — `EasyCollectorCredentialGuard` 테스트로 검증).
- 사람이 하는 인증은 **Collector가 연 바로 그 visible 브라우저 창의, 사이트 자체 로그인 UI**에서 직접 이루어진다. Collector 프로세스는 그 UI를 절대 대신 클릭/입력하지 않는다(mock 테스트에서도 프로덕션 코드 경로는 손대지 않고, 테스트 전용 hook만 별도로 존재 — §4).
- Collector ↔ EASY 인증은 **collector별 고유 Bearer 토큰**(등록 시 1회 발급, EASY DB엔 SHA-256 해시만). `EasyCollectorAgentTokenFilter`가 기존 `ChartAgentTokenFilter`의 narrow-allowlist 패턴을 그대로 재사용(같은 `SecurityConfig` 삽입 위치, 같은 "`/api/admin/**` catch-all보다 먼저" 원칙) — 단 공유 env 토큰이 아니라 매 요청 DB 조회로 검증.
- **Local Collector는 inbound 포트를 전혀 열지 않는다.** EASY 쪽으로 나가는 poll(GET/POST)만 수행 — 학원 PC에 외부에서 접속할 대상 자체가 없다.

---

## 4. 실제 Windows Visible-Browser E2E 결과 (실행 완료, mock만)

`local-collector/test:e2e-mock` — **실제로 이 Windows 머신에서 실행**, exit 0, `E2E LIFECYCLE: PASS`.

- 실제 headed Chromium 창이 화면에 떴다(WAITING_FOR_USER_AUTH 시점 스크린샷 캡처 — 세션 대화 기록에 첨부, repo에는 커밋 안 함).
- 잘못된 토큰 → collector의 heartbeat 자체가 HTTP 401 → 실행 중단 (`unauthorizedRejected: true`).
- WAITING_FOR_USER_AUTH가 서버 쪽에서도 관측됨 → **같은 browser/context/page** 재개(새 창 아님) → 폼 채우기+조회 → 다운로드.
- 다운로드 파일 SHA-256을 Node 프로세스 내부 계산값과 `sha256sum`(외부, 독립) 양쪽에서 대조 — **완전 일치**.
- EASY(fake backend)로는 `{filename, size, sha256}`만 전송됨 — fake backend 코드 자체에 파일 바이트를 받을 필드가 없음(검증됨).

**사람 인증 클릭을 대신한 부분(정확히 명시)**: 실시간 사람이 없는 무인 자동 검증 실행이라, `collector.js`의 `testHooks.onWaitingForAuth` — **production 코드 경로에는 전혀 관여하지 않는, 테스트 스크립트 전용 훅**이 Collector가 연 바로 그 페이지에서 로그인 버튼을 클릭했다. 프로덕션에서 이 훅은 항상 `undefined`이고, 실제 사람 없이는 절대 진행되지 않는다.

---

## 5. 다운로드 저장 위치 / METADATA / SHA-256

- 기본 저장 위치: **git repo 밖**, `~/easy-local-collector/work-input/`(`src/config.js`의 `DEFAULT_DOWNLOAD_DIR`, `os.homedir()` 기준). E2E 테스트 자체는 `local-collector/.e2e-downloads/`를 썼지만 이는 `.gitignore` 처리되어 커밋되지 않음.
- EASY로 전송되는 metadata: `filename`(로컬 저장 파일명), `size`(byte), `sha256` — **파일 내용 자체는 절대 전송하지 않음**(controller/service/entity 어디에도 파일 바이트를 받는 필드 없음).
- 이번 세션 mock 실행 실측값: `task-1-card-usage-mock.csv`, 143 bytes, `sha256=3b5ab0e801a9038349681db83d7feba18028a4958f717511707b2ff56d92bd2c` (Node 계산값과 외부 `sha256sum` 계산값 일치 확인).

---

## 6. ACCOUNTING WRITE = 0 (검증 방식)

- 코드 리뷰: `EasyLocalCollectorService`, `EasyCollectorTaskService` 어디에도 `EasyTransaction`/`EasyJournalService`/`EasyAccount*` 레포지토리 의존성이 없음.
- 구조적 테스트: `EasyCollectorNoAccountingWriteTest` — 두 서비스의 선언된 필드 타입을 reflection으로 검사해 회계 관련 타입이 하나라도 섞이면 빌드가 즉시 실패하도록 고정.
- 이번 phase는 명시적으로 **"RAW 다운로드 + metadata 보고"까지만** — PENDING_REVIEW/EasyCardEvent/EasyTransaction 연결은 전부 다음 단계(§9 STEP 8)로 미룸.

---

## 7. 기존 EASY collection / 카드 회계와 아직 연결 안 됨 (중요)

- `EasyCollectionAdapter`, `easy_collection_sources/connections/runs/raw_records`, `EasyBusinessCardAccountingService.observePurchase(...)`, Phase 5A.1의 `EasyCardEvent → PENDING_REVIEW → approve → EasyTransaction → EasyJournalService` 파이프라인 — **전부 읽고 이해했지만 이번 PR에서 하나도 수정하지 않았고, 이번 PR의 어떤 코드도 그것들을 호출하지 않는다.**
- 즉 지금 Collector가 무언가를 다운로드해도, **그 결과는 EASY 장부/검토 큐 어디에도 자동으로 들어가지 않는다.** `easy_collector_tasks.downloaded_filename/size/sha256`에만 기록되고 끝난다.
- 이 연결(raw → normalize → dedupe → PENDING_REVIEW)은 실제 카드사 데이터의 실제 필드를 본 뒤에만 설계한다(§9 STEP 7-8) — 이번 라운드에서 미리 만들지 않음.

---

## 8. PRE-EXISTING TEST FAILURES (이번 PR과 무관, 원인 확인됨)

다음 5개 클래스가 이번 PR 변경 전(완전히 손대지 않은 별도 worktree)에서도 **동일하게 23/23 전부 실패**함을 먼저 확인한 뒤, 이번 PR 작업 범위에서 제외했다:

| 클래스 | 근본 원인 |
|---|---|
| `AuthFlowIntegrationTest` | `@SpringBootTest` 실 MySQL 연결 필요 — 이 환경에 로컬 MySQL 없음(Docker daemon 미기동, §9) |
| `NaverOAuth2SecurityRegressionTest` | 상동 |
| `OwnershipGuardIntegrationTest` | 상동 |
| `AcademyLmsSecurityIntegrationTest` | `@WebMvcTest` 슬라이스지만 `AcademyLmsShellModel`이 `MidtermCampaignRepository` 빈을 못 찾음(EASY/이 PR과 무관한 기존 테스트 fixture 공백) |
| `SubdomainLmsPathSecurityIntegrationTest` | 상동 |

이번 PR이 실제로 건드린/검증한 테스트: `domain.easy.*`(41개 결과 파일, 전부 PASS) + `EasyCollectorAgentTokenFilterTest`(무DB 단위테스트, PASS) + `ChartAgentTokenFilterTest`(참고용 회귀, PASS). `compileJava`/`test`(위 스코프)/`bootJar` 전부 PASS.

---

## 9. DOCKER / MYSQL 상태

- 이 세션 환경에는 **로컬 MySQL이 실행 중이지 않다.** `docker --version`은 동작하나 Docker Desktop 데몬 자체가 기동돼 있지 않음(`docker ps` → "cannot connect to daemon" 오류 확인).
- `scripts/run-local.sh`(docker-compose 기반 로컬 MySQL 기동 스크립트)는 **이번 세션에서 실행하지 않았다** — 위 §8 실패군 때문에 필요성은 확인했으나, 전체 백엔드+실DB 기동은 이번 작업 범위/시간상 시도하지 않기로 판단.
- 따라서 이번 검증은 (a) Java 쪽은 Mockito 단위테스트 + `@WebMvcTest` 슬라이스(무DB)로, (b) 브라우저 메커니즘 쪽은 Node E2E(실제 Playwright, fake backend)로 **분리 검증**했다 — 풀스택 1-프로세스 E2E(진짜 Spring Boot + 진짜 MySQL + 진짜 Node collector)는 아직 안 했다. 학원 PC에 로컬 MySQL이 있다면 STEP 1~2에서 이 풀스택 검증을 먼저 해볼 가치가 있다.

---

## 10. REAL CARD COMPANY POC가 아직 NOT READY인 이유

1. **DOM/selector를 추측하지 않는다는 원칙** — 실제 카드사 사이트의 로그인 후 메뉴 구조, 조회 폼 selector, 다운로드 버튼 selector를 전혀 모른다. 이번 세션은 mock 사이트(우리가 만든 HTML)로만 검증했다.
2. **어떤 카드사인지도 아직 미정** — 이전 historical 감사(EasyShop golden sample)에서 에듀홀릭 카드매출 정산은 국민/롯데/삼성/하나SK/BC 5개사가 섞여 있었지만, 이건 "카드매출 정산입금"(여신금융협회 방향)이지 "학원이 직접 쓰는 사업용 카드"(이번 Collector의 목표 방향)가 어느 카드사인지는 별도 확인이 필요하다.
3. **실제 로그인 자체를 한 번도 안 해봤다** — 공동인증서 UI가 실제로 어떻게 생겼는지, 로그인 성공 신호(authDetect)를 무엇으로 잡아야 하는지 전혀 모른다.
4. **collector → EASY 회계 연결이 아직 설계되지 않음**(§7) — raw 파일을 받아도 그걸 어떤 dedupe key로, 어떤 EasyCardEvent 필드로 매핑할지는 실제 파일의 실제 컬럼을 본 뒤에만 정할 수 있다(추측 금지 원칙, 이전 라운드부터 일관).

---

## 11. NEXT STEPS (오후 학원 PC)

```
STEP 1  학원 PC에서 latest main + PR #369 확인
        git fetch origin --prune
        git rev-parse origin/main   # 1cd88f64 여야 함 (변경 없었다면)
        gh pr view 369 --repo woori73/feel2real-buildit

STEP 2  PR #369 CI 확인 및 최종 diff audit
        gh pr checks 369 --repo woori73/feel2real-buildit
        (필요시 code-review 스킬로 최종 diff 재검토)

STEP 3  실제 카드사 1곳을 선정하되 DOM/selector를 추측해서 구현하지 않는다.
        (§10-2 미정 사항 먼저 사용자와 확정)

STEP 4  사용자가 학원 PC visible browser에서 직접 로그인/인증
        (Collector가 대신 하지 않음 — 사람이 직접)

STEP 5  실제 최근 며칠 거래를 조회하고 공식 Excel/CSV 1개를 직접 다운로드하여
        REAL_SOURCE_SAMPLE_001 확보

STEP 6  raw 파일은 GitHub에 올리지 않고 local work-input에만 보관
        (기존 work-input/easyshop-real/ 과 동일한 관례 — repo 밖, .gitignore 대상)

STEP 7  실제 파일 schema를 확인한 뒤에만 parser + workflow adapter를 설계
        (src/workflows/ 에 새 파일 하나, cardCompanyTestWorkflow.js 형태 그대로 모방)

STEP 8  이후 collector output을 EASY review/accounting pipeline과 연결
        (raw → normalize → dedupe → PENDING_REVIEW → EasyCardEvent,
         관찰 진입점은 이미 존재: EasyBusinessCardAccountingService.observePurchase)
```

**금지 사항 (이어서도 유지):**
- MERGE 금지 / DEPLOY 금지 / Production DB 변경 금지
- 실제 카드 로그인은 STEP 4에서 **사람이 직접**만 — Collector가 절대 대신 로그인하지 않음
- 실제 카드 자동수집(반복 무인 실행)은 이번 라운드 목표 아님 — 1회 사람 관찰 하에 REAL_SOURCE_SAMPLE_001 확보까지만
- 다른 Agent/NewStart worktree 접촉 금지

---

## 12. PRODUCTION / DEPLOYMENT

| 항목 | 상태 |
|---|---|
| Production DB 변경 | NO |
| 실제 카드사/금융사이트 접속 | NO (mock만) |
| 실제 credential 입력 | NO |
| Main merge | NO |
| Production deploy | NO |

---

## 13. ACADEMY RESUME PROMPT

> **새 Claude 세션(학원 PC)에 그대로 복사해서 실행하세요.**

```
## ACADEMY RESUME PROMPT — EASY Visible Local Browser Collector Phase 2

### Repository
woori73/feel2real-buildit

### EASY Branch
feat/easy-visible-local-collector-v1

### Latest commit
c5b84dd0f7d8c290ec84275d44920a9228e21246

### PR
#369 (OPEN, MERGE 안 됨)

### HANDOFF 경로
docs/easy/EASY_VISIBLE_LOCAL_COLLECTOR_HANDOFF_2026-09-22_HOME_TO_ACADEMY.md

### 완료 작업 (V1 foundation)
1. easy_local_collectors / easy_collector_tasks (V991, additive)
2. 7단계 state machine + 격리 가드(business/collector) + 중복작업 가드 + credential-leakage 가드
3. EasyCollectorAgentTokenFilter (ChartAgentTokenFilter narrow-route 패턴 재사용)
4. local-collector/ — Playwright(Node.js) 기반 visible browser agent, LLM/Qoder/유료 provider 불필요
5. mock 사이트 + fake backend로 실제 Windows visible-browser E2E 전체 lifecycle 통과 확인
6. Java 신규 44 tests PASS, EASY regression PASS, compileJava/bootJar PASS
7. accounting write 경로 0건(구조적으로 검증)

### 미완료 작업 (Phase 2 시작점)
1. 실제 카드사 선정 (에듀홀릭이 실제 쓰는 사업용 카드가 어디인지 확정 — historical 감사에서는
   "카드매출 정산" 방향 카드사 5곳만 확인됐고, "사업용 지출 카드" 방향은 미확인)
2. 사람이 학원 PC에서 직접 로그인 → 실제 최근 며칠 조회 → 공식 CSV/Excel 1건 다운로드
   (REAL_SOURCE_SAMPLE_001, GitHub에 올리지 않고 local work-input에만)
3. 실제 파일 스키마 확인 후에만 workflow adapter 신규 파일 작성
4. Collector 출력 → EASY review/accounting pipeline 연결 설계
   (raw → normalize → dedupe → PENDING_REVIEW → EasyCardEvent.observePurchase 경로)
5. (선택) 로컬 MySQL 기동(scripts/run-local.sh) 후 진짜 풀스택 1-프로세스 E2E 검증
   (이번 세션은 Docker daemon 미기동으로 스킵함)

### 금지 사항
- MERGE 금지 / DEPLOY 금지
- Production DB 변경 금지
- 실제 카드 로그인은 사람이 직접만 — Collector가 대신 로그인 금지
- DOM/selector 추측 금지 — 실제 파일/화면을 본 뒤에만 구현
- NewStart/다른 Agent worktree 접촉 금지

### 학원 PC Git 재개 절차
git fetch origin --prune
git worktree add -b <새 작업 브랜치 또는 기존 브랜치 checkout> <경로> origin/feat/easy-visible-local-collector-v1
git status
gh pr view 369 --repo woori73/feel2real-buildit
# 이 문서(HANDOFF) 읽고 STEP 1부터 시작

### MERGE / DEPLOY
NO / NO
```
