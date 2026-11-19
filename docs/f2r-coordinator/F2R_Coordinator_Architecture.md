# F2R AI Coordinator V1 — 최종 설계

> 상태: **설계안. 구현·DB 변경·배포는 하지 않았다.** 실제 운영 배포는 별도 승인 후 진행한다.
> 근거 조사: [F2R_COORD_Audit.md](F2R_COORD_Audit.md)

## 1. 원칙

1. 새 학원 관리 시스템을 만들지 않는다. 기존 출결(`EasyAttendanceService`), 위키 MCP, AI 업무 비서 승인 흐름, 학부모 안내문 초안, S3, JWT 역할을 재사용한다.
2. 서버가 작업의 상태와 권한을 가진다. PC와 휴대폰은 서버에 "일을 받고 결과를 보고"할 뿐이다.
3. AI는 F2R DB에 직접 쓰지 않는다.
   - DB에 쓰는 모든 동작은 서버의 기존 서비스 함수로만 실행한다.
   - 그 앞에 승인 단계를 둔다: 출결·배정·게시·발송.
4. 외부 AI에는 업무에 필요한 최소 정보만 보낸다.
   - 학생 실명·연락처는 서버에 남긴다.
   - AI에는 가명(S01…)과 작업 ID만 보낸다.

## 2. 구성도

```
[Galaxy Z Fold6 앱]──HTTPS──▶[F2R 서버(Spring, 기존 EC2)]◀──HTTPS(아웃바운드 폴링)──[PC Agent(Windows)]
  음성·문자·사진               /api/coord/** (신규)                                    ClaudeRunner → claude -p
  승인·거절·수정               coord_* 테이블 (신규)                                   CodexRunner  → codex exec
  카카오톡 공유(사용자 발송)    기존 서비스 호출(출결·위키·안내문·S3)                    작업 폴더(job별 격리)
                                │
                                └─ /mcp (기존 MCP 게이트웨이 + f2r_* 도구 추가) ◀── PC의 Claude Code·Codex가 호출
```

- **휴대폰 ↔ PC는 직접 통신하지 않는다.** 항상 서버를 거친다.
- PC가 꺼져 있으면 작업은 서버에서 `queued` 상태로 기다린다.
- PC Agent는 포트를 열지 않는다. 서버로 나가는 연결만 쓴다(`local-collector`와 같은 방식).

## 3. 작업 상태 머신

```
queued ──(PC가 가져감)──▶ running ──(결과 업로드)──▶ review_required ──(승인)──▶ approved ──(서버 실행·확인)──▶ completed
   │                        │                              │
   │                        └─(오류)─▶ retry_pending ─(재시도 ≤3)─▶ queued
   │                        └─(오류, 재시도 초과·복구 불가)─▶ failed
   └─(사용자 취소)─▶ cancelled                               └─(거절)─▶ rejected   (수정 요청 시 새 작업으로 다시 queued)
```

- **임대 시간(lease)**
  - PC가 `running`으로 가져간 작업은 10분간 그 PC가 붙잡는다. 작업이 진행되는 동안 heartbeat로 임대 시간을 연장한다.
  - 임대 시간이 끝나면 `retry_pending`으로 바뀐다. PC가 꺼지거나 프로그램이 죽었을 때를 복구하는 장치다.
- **중복 방지**
  - 앱은 명령마다 `requestId`(UUID)를 보낸다. 서버는 `(user_id, request_id)`를 UNIQUE로 둔다. 네트워크 재전송으로 같은 명령이 두 번 와도 한 번만 처리한다.
  - 승인할 때는 `planHash`를 확인한다(AI 업무 비서와 같은 방식). 사용자가 본 내용과 실제로 실행될 내용이 같을 때만 실행한다.
- **AI가 필요 없는 작업은 PC를 거치지 않는다.**
  - 출결처럼 서버만으로 끝나는 작업은 서버에서 바로 처리한다: `queued → review_required → approved → completed`.
  - 명령 해석은 앱의 규칙과 서버의 기존 `AssistantCommandParser`로 한다.

## 4. 데이터 모델 (신규, 구현 시 V1123부터)

> `docs/WORKFLOW.md` §4 번호 정책을 따른다. 기존 테이블은 바꾸지 않는다.

| 테이블 | 핵심 컬럼 | 비고 |
|---|---|---|
| `coord_devices` | id, user_id, kind(`MOBILE`/`PC`), name, token_hash(SHA-256, UNIQUE), status(`ACTIVE`/`REVOKED`), capabilities JSON(claude/codex 버전·로그인 방식), last_seen_at, created_at | `easy_local_collectors` 방식을 그대로 따른다 |
| `coord_jobs` | id(UUID), user_id, business_id, request_id, kind(`ATTENDANCE`/`DOC_TO_HTML`/`REVIEW_GEN`/`PARENT_NOTICE`/`FREE_TEXT`), runner(`CLAUDE`/`CODEX`/`SERVER`), target_device_id, status, attempt, lease_until, input JSON(가명화), plan JSON, plan_hash, result JSON, error, created_at, updated_at | UNIQUE(user_id, request_id) |
| `coord_job_events` | id, job_id, at, actor(`MOBILE`/`PC`/`SERVER`/`USER`), from_status, to_status, message | 감사 로그. 수정·삭제 API는 두지 않는다 |
| `coord_job_files` | id, job_id, role(`INPUT_IMAGE`/`RESULT_HTML`/`LOG`), s3_key, sha256, bytes, expires_at | 사진은 30일 뒤 지운다(배치 작업) |
| `easy_student_links` | easy_student_id, lms_student_id NULL, wiki_student_name NULL, confirmed_by, confirmed_at | **학생 명부 세 곳 연결**. 관리자가 직접 확인해 만든다 |

- **CRUD 범위**(CLAUDE.md 규칙)
  - 기기: 등록·이름 수정·해지(REVOKED). 물리 삭제는 하지 않는다.
  - 작업: 생성·취소·승인·거절. 삭제는 하지 않는다. 감사를 위해서다.
  - 파일: 보관 기간이 끝나면 S3 객체만 지우고 행은 남긴다.
  - 학생 연결: 생성·수정·해제. 권한은 ADMIN만 가진다.

## 5. 서버 API (신규, `/api/coord/**`)

| API | 호출자 | 설명 |
|---|---|---|
| `POST /api/coord/devices/pair` | 웹(ADMIN, 로그인) | 기기 등록. 등록 토큰을 한 번만 보여 주고 QR로 앱이나 PC에 넘긴다 |
| `POST /api/coord/mobile/session` | 앱(기기 토큰) | 짧은 접근 토큰(1시간)을 발급한다. 기기 토큰은 앱의 Keystore에만 보관한다 |
| `POST /api/coord/jobs` | 앱 | 명령 접수(`requestId`, kind, runner, text, 첨부 ID) |
| `POST /api/coord/jobs/{id}/files` | 앱 | 사진 업로드 → S3. 25MB 이하, JPEG/PNG만 |
| `GET /api/coord/jobs?since=` | 앱 | 작업 목록·상태(앱이 5초마다 조회) |
| `POST /api/coord/jobs/{id}/approve` · `/reject` · `/revise` | 앱 | `planHash`가 같을 때만 승인한다. 수정은 새 작업으로 처리한다 |
| `POST /api/coord/agent/heartbeat` | PC(기기 토큰) | 연결 상태와 실행 환경 보고 |
| `GET /api/coord/agent/next-job` | PC | 대기 중인 작업 1건을 가져가고 임대 시간을 잡는다. 없으면 204 |
| `GET /api/coord/agent/jobs/{id}/input` | PC | 입력(가명화)과 사진을 받을 일회용 다운로드 URL |
| `POST /api/coord/agent/jobs/{id}/progress` · `/result` · `/failed` | PC | 진행 보고, 결과 업로드(HTML 등), 오류 보고 |

- PC 토큰 필터는 `EasyCollectorAgentTokenFilter`처럼 **경로 허용 목록**만 통과시킨다. 그 밖의 경로는 거부한다.
- 앱 토큰이 할 수 있는 일은 `/api/coord/**`와 기존 `/api/auth/me`로 한정한다.

## 6. AI 실행 환경 (Runner)과 과금 구분

### 6.1 어댑터

| 어댑터 | 실행 명령(요지) | 입력·출력 |
|---|---|---|
| ClaudeRunner | `claude -p "<지시문>" --output-format json --allowedTools "Read,Write" --permission-mode dontAsk --max-turns 20` (cwd = 작업 폴더) | 사진은 작업 폴더에 넣고, 지시문에 그 경로를 적는다. 결과는 `result.html`과 JSON(`result`·`session_id`·`total_cost_usd`) |
| CodexRunner | `codex exec "<지시문>" -C <작업폴더> -i photo.jpg --sandbox workspace-write --json -o last.txt --skip-git-repo-check --ephemeral` | JSONL 이벤트와 마지막 메시지 파일 |

- 작업 폴더는 작업마다 새로 만든다: `%LOCALAPPDATA%\F2R\jobs\<jobId>`.
  - 그 안에 `.mcp.json`·hooks·저장소 파일을 두지 않는다. Claude Code `-p`는 프로젝트의 hooks와 `.mcp.json`을 확인 없이 읽기 때문이다.
- Bash·셸 도구는 허용하지 않는다(V1). Windows 기본 환경의 Claude Code에는 샌드박스가 없기 때문이다(WSL2에서만 지원).
- MCP 연결
  - PC Agent가 작업마다 **그 작업 전용 짧은 MCP 토큰**을 받아 넘긴다. Claude Code는 `--mcp-config` 파일의 header(또는 `headersHelper`)로, Codex는 `bearer_token_env_var`로 넘긴다.
  - 헤드리스 실행에서는 OAuth 로그인을 진행할 수 없기 때문이다.

### 6.2 구독과 API 과금 (공식 문서 확인, 2026-11-19)

| 방식 | Claude Code | Codex | 판단 |
|---|---|---|---|
| **A. 구독 로그인**(Pro/Max, ChatGPT Plus/Pro) | 구독 한도를 쓴다. 추가 과금은 없다. 단, PC에 `ANTHROPIC_API_KEY`가 **있으면 `-p`는 항상 키를 쓴다**(과금). 무인 실행용 `claude setup-token`(1년)이 있다. | 플랜 한도를 쓴다. `auth.json`을 보관한다. OpenAI는 자동화에는 "신뢰된 비공개 실행기에서만"이라고 안내한다. | **정책 회색지대.** Anthropic 문서는 구독 로그인을 "구매자 본인의 일반적인 사용"용이라고 하고, 제품·서비스에는 API 키를 쓰라고 한다. 원장 본인의 PC에서 본인 업무만 처리한다면 본인 사용에 가깝다. 그래도 **서버가 작업을 자동으로 내려보내는 구조라서 확인이 필요하다.** |
| **B. API 키** | 토큰 단위 과금(§9). Anthropic이 공식 권장한다. | 토큰 단위 과금. OpenAI가 공식 권장한다. | 정책상 안전하다. 비용이 측정 가능하다. |

- **권장**: V1 시험 단계는 **B(API 키)**로 시작하고, 월 비용 상한을 둔다(`--max-budget-usd`, 서버 일일 상한). A를 쓰려면 Anthropic·OpenAI에 서면으로 문의해 확인받은 뒤 전환한다.
- 쿠키 추출, 비밀번호 저장, 웹 자동화는 쓰지 않는다(지시서 원칙).
- 공식 출처(2026-11-19 확인):
  - Claude Code: `code.claude.com/docs/en/` 아래 `headless`, `cli-reference`, `authentication`(인증 우선순위·`setup-token`), `legal-and-compliance`(구독 로그인 사용 범위), `mcp`(헤드리스 OAuth 불가·`headersHelper`), `remote-control`, `setup`(Windows), `agent-sdk/overview`(SDK는 API 키)
  - Codex: `developers.openai.com/codex/noninteractive`, `/codex/auth`, `/codex/auth/ci-cd-auth`, `/codex/mcp`, `/codex/windows`, `github.com/openai/codex`(소스 `codex-rs/exec/src/cli.rs`의 `exec` 플래그)
  - 일부 Codex 페이지는 검색 결과의 요약문으로만 확인했다. 구현 전에 설치된 버전의 `--help`로 다시 확인한다.
- MCP는 F2R 업무 도구를 AI에 제공하는 통로일 뿐이다. 모델을 실행하는 것은 위 CLI다.

## 7. 기능별 흐름

### 기능 1. 음성·문자 AI 호출

1. 앱(Android `SpeechRecognizer`, 한국어)이 받아 적은 글을 화면에 보여 준다.
2. 첫 단어("클로드"/"지피티")로 실행 환경을 고른다. 첫 단어가 없으면 앱에서 고른 모드를 따른다.
3. 받아 적기 신뢰도가 낮거나 명령이 모호하면 "○○라고 하셨나요?"로 다시 확인한다.
4. `POST /api/coord/jobs` → PC가 처리 → 결과를 글로 보여 주고 음성(`TextToSpeech`)으로 읽어 준다.
5. V1은 앱이 켜져 있을 때만 듣는다. 상시 대기 호출어는 다음 단계에서 한다.

### 기능 2. 학습지 촬영 → HTML

1. 앱의 CameraX로 촬영한다.
   - 촬영 전 점검: 문서 테두리(ML Kit Document Scanner 또는 윤곽 검출), 초점, 흔들림(가속도계·선명도).
   - 촬영 뒤 미리보기에서 다시 찍기를 할 수 있다.
2. 업로드 → S3(`coord_job_files`, 30일 보관).
3. `DOC_TO_HTML` 작업 → 지정 PC의 ClaudeRunner로 간다. 지시문의 규칙은 다음과 같다.
   - 원문을 고치거나 빠뜨리지 않는다.
   - 읽기 불확실한 부분은 `<mark class="uncertain">`로 표시한다.
   - 문항·선택지·표·수식(MathML 또는 원문 그대로)을 구분한다.
4. 결과 HTML → 서버에서 jsoup Safelist로 정화한다(스크립트 제거). 그다음 **검토 대기로 저장한다**(저장 위치는 결정 필요: 위키 저장소 `AI_PROPOSED` 또는 제작소 리뷰).
5. 앱이나 웹에서 원본 사진과 HTML을 나란히 보고 승인 → 게시한다. 승인 전에는 공개되지 않는다.

### 기능 3. 출결("김민수 학생 등원했어")

1. 서버만으로 처리한다(AI 불필요). 이름으로 `easy_students`를 검색한다(재원생만).
2. 결과에 따라 처리한다.
   - 0명: "찾지 못했습니다"
   - 2명 이상(동명이인): 학교·학년·반을 보여 주고 고르게 한다
   - 1명: 오늘 수업 반·시간을 보여 준다
3. 오늘 이미 `CHECK_IN`이 있으면 "이미 ○시 ○분에 등원 처리됨"으로 답하고 다시 기록하지 않는다.
4. 확인 화면에서 「등원 기록」을 한 번 누른다(승인). 그다음 `EasyAttendanceService.record`를 호출한다.
5. 다시 읽어서 확인(`studentHistory`)한 뒤 "김민수 학생 16시 05분 등원 기록했습니다"로 음성 응답한다.

### 기능 4. 복습 문제("지피티, 김민수 오답으로 복습 문제 5개")

1. `easy_student_links`로 위키·LMS 학생을 찾는다. **연결이 없으면 중단하고 연결을 요청한다.**
2. 서버가 오답·원문항·채점 근거를 모은다(위키 `get_csat_weakness`·`get_csat_problem_context`).
   - AI에는 **가명과 문항 자료만** 넘긴다.
3. 생성: CodexRunner(또는 Claude)가 같은 유형 5문항, 정답, 해설, 근거 문항 번호를 만든다.
4. 검증: **다른 실행에서 따로 확인한다.** 정답 유일성, 근거와의 일치, 형식을 본다. 불일치하면 `review_required`에 경고를 붙인다.
5. 위키에 `AI_PROPOSED`로 등록한다 → 강사가 승인 → 학생에게 배정한다(LMS 학습자료 배정은 승인 뒤에만).

### 기능 5. 학부모 안내문

1. AI가 초안을 쓴다. 기존 `save_parent_notice_draft`와 같은 저장소(`/dashboard/parent-notices`)에 둔다.
2. 앱에 받는 대상(반·인원 수만)과 본문을 보여 준다 → 사용자가 승인한다.
3. Android 공유 인텐트(`ACTION_SEND`, text/plain)로 카카오톡을 연다. 받는 사람은 **사용자가 카카오톡에서 직접 고르고 보낸다.**
4. 친구 목록을 추출하거나 자동으로 보내지 않는다. 공식 카카오 비즈 메시지는 따로 검토한다.

## 8. 승인 정책

| 동작 | 승인 | 승인자 |
|---|---|---|
| 조회(학생 검색, 출결 조회, 작업 상태) | 불필요 | — |
| 출결 기록·정정 | 앱 확인 화면에서 1번 누름 | 로그인한 ADMIN/TUTOR(해당 사업체 권한) |
| HTML 게시, 문항 위키 반영 | 검토 화면에서 승인 | ADMIN/TUTOR |
| 학생에게 과제 배정 | 강사 승인 | ADMIN/TUTOR |
| 외부 메시지(카카오톡) | 앱 승인 뒤 **사용자가 카카오톡에서 직접 보냄** | 사용자 본인 |
| 학생 명부 연결 | 관리자 확인 | ADMIN |

## 9. 예상 비용 (월, 추정)

- 단가: 2026-11-03 기준 공식 표다. 백만 토큰당 입력/출력 가격이며, 실제 단가는 가격 페이지에서 다시 확인한다.
  - 상위 모델급: $4 / $20
  - 중간 모델급: $2 / $10
  - 경량 모델급: $0.10 / $0.50
- 이미지 한 장은 긴 변을 약 1,600px로 줄였을 때 약 1.5천~1.6천 토큰으로 잡았다(추정).

| 작업 | 가정(1건) | 중간 모델급 | 상위 모델급 |
|---|---|---|---|
| 학습지 → HTML | 입력 약 3.6천(사진+지시), 출력 약 4천 | 약 $0.05 | 약 $0.09 |
| 복습 5문항(생성+검증 2회) | 회당 입력 8천, 출력 3천 | 약 $0.09 | 약 $0.18 |
| 출결·안내문 | 서버 처리 / 짧은 생성 | $0 / 약 $0.01 | $0 / 약 $0.02 |

- **월 추정**: 학습지 40건, 복습 60건, 안내문 30건이면 API 직접 계산으로 **약 $8~15**다.
  - Claude Code·Codex 같은 에이전트 CLI는 도구 설명·여러 차례 대화가 더해져 **약 3~5배로 잡는다 → 월 $25~75**.
  - 시험 단계에서 `total_cost_usd`(Claude, 클라이언트 추정치)와 사용량 대시보드로 실측한다.
- 구독 방식(A)이면 추가 과금은 없고 플랜 한도 안에서 처리된다. 다만 §6.2의 정책 확인이 먼저다.
- 인프라: 기존 EC2·RDS를 그대로 쓴다. S3 사진은 30일만 보관해 월 1GB 미만이며 1달러 미만으로 본다.
- 음성 인식·합성·카카오톡 공유: Android 기본 기능이라 추가 비용이 없다.
- Android 배포: 직접 설치(APK)면 0원이다. Play Console에 올리려면 개발자 등록비가 든다(일회성, 금액은 구글 공식 확인).

## 10. 보안 위험과 대응

| 위험 | 대응 |
|---|---|
| 사진 속 문구가 AI에게 지시를 내림(프롬프트 주입) | 사진과 HTML은 자료로만 다룬다. AI 실행 권한은 Read·Write(작업 폴더)로 한정하고 셸은 쓰지 않는다. 결과는 jsoup로 정화하고, 게시 전 사람이 승인한다 |
| PC에서 AI가 파일·명령을 함부로 실행 | 작업별 격리 폴더, 셸 도구 차단, `--max-turns`·예산 상한, 저장소·`.mcp.json` 없는 폴더 |
| PC 토큰·CLI 자격 증명 유출 | 기기 토큰은 해시로만 저장하고 언제든 해지할 수 있다. 토큰은 Windows DPAPI(사용자 단위)로 암호화해 보관한다. CLI 자격 증명은 각 CLI의 기본 저장소에 두고 Agent가 읽지 않는다 |
| 휴대폰 분실 | 기기 토큰은 Android Keystore에 둔다. 웹 관리 화면에서 해지할 수 있다. 접근 토큰은 1시간짜리다. 앱을 열 때 생체 인증(선택) |
| 학생 개인정보가 외부 AI로 나감 | 서버가 가명(S01…)으로 바꿔 보낸다. 실명·학교·연락처는 서버에 남긴다. 학습 데이터에 넣지 않는다(기존 원칙) |
| 승인 없이 DB 변경·발송 | DB 변경은 서버의 기존 서비스 함수로만, `planHash` 승인 뒤에만 한다. 카카오톡은 사용자가 직접 보낸다. MCP 쓰기 도구는 **초안만** 만든다 |
| 중복 실행 | `requestId` UNIQUE, 작업 임대 시간, 출결 같은 날 `CHECK_IN` 중복 검사 |
| 구독 약관 위반 | §6.2. API 키를 기본으로 하고, 구독 방식은 서면 확인 뒤에만 쓴다 |
| 사진·음성 보관 | 사진 30일(설정 가능). 음성 원본은 서버로 보내지 않고 받아 적은 글만 보낸다 |
| 감사 추적 | `coord_job_events` 모든 상태 변경(누가·언제·무엇을), 기존 `unified_assistant_audit` 연계 |

## 11. 구현 시 변경 예정 파일 (아직 만들지 않음)

| 구분 | 파일(예정) |
|---|---|
| DB | `db/migration/V1123__coordinator_devices_jobs.sql`, `V1124__easy_student_links.sql` + Entity + `docs/DOMAIN.md` |
| 서버 | `domain/coordinator/**`(Controller·Service·Repository·Entity), `config/security/CoordAgentTokenFilter.java`, `CoordMobileTokenFilter.java`, `SecurityConfig.java`(필터·허용 목록 추가), `domain/mcp/McpGateway.java`(f2r_* 도구 추가), `domain/easy/service/EasyAttendanceService.java`(이름 검색·중복 확인 **추가만**, 기존 동작 불변) |
| 문서 | `docs/API.md`, `docs/FLOWS.md`, `docs/DOMAIN.md`, `docs/INDEX.md` |
| PC Agent | `coord-agent/`(신규 Node.js 프로젝트, `local-collector` 구조 참고) |
| Android | 별도 저장소 권장(`f2r-coordinator-android`) — Kotlin, CameraX |
| 테스트 | given/when/then 단위·MySQL 통합 테스트, `build-check.yml` 목록 추가 |

기존 DB 테이블·인증·장부·학원 운영 기능은 바꾸지 않는다. 출결 서비스에는 메서드만 **추가**한다.
