# F2R AI Coordinator V1 — 기존 시스템 조사 (Audit)

- 조사일: 2026-11-19
- 기준 코드: `woori73/feel2real-buildit` main `300adb94` (마이그레이션 최신 V1122)
- 범위: 코드·문서·공개 운영 화면 읽기만 했다. DB·설정·운영 환경은 바꾸지 않았다.
- 표기
  - **확인**: 코드나 운영 화면에서 직접 확인했다.
  - **문서**: 공식 문서로 확인했다.
  - **미확인**: 확인하지 못했다. 다음 단계에서 확인한다.

## 1. 저장소와 배포 환경

| 항목 | 내용 | 근거 |
|---|---|---|
| 저장소 | `woori73/feel2real-buildit` 하나. Spring 앱, 위키 백엔드(Python), 로컬 수집기(Node), GPU 작업자가 함께 들어 있다. | 확인 |
| 운영 | AWS EC2 + systemd. `prd` 프로필. 배포는 GitHub Actions `deploy-ec2.yml`을 수동 실행한다. | 확인 (`docs/AWS_DEPLOYMENT.md`) |
| DB | RDS MySQL 8.4.8. 스키마는 Flyway가 관리하고, Hibernate는 `ddl-auto: validate`로 검증만 한다. | 확인 (운영 flyway-history 읽기 전용 조회) |
| 파일 | S3(`S3_BUCKET`). 업로드 한도는 200MB(multipart). | 확인 (`application-prd.yaml`) |
| 비밀값 | EC2 `/etc/f2r/env`(systemd EnvironmentFile). YAML에는 비밀값을 두지 않는다. | 확인 (CLAUDE.md) |
| 위키 백엔드 | 별도 systemd 유닛 `buildit-wiki.service`(FastAPI + SQLite). Spring이 HMAC 서명 assertion을 붙여 프록시한다. | 확인 (`docs/AWS_DEPLOYMENT.md` 471행) |

## 2. 프레임워크·DB·인증

- **스택**: Spring Boot 4.0.6, Java 21, Thymeleaf SSR, JPA, MySQL. 의존성에 `spring-boot-starter-websocket`, `anthropic-java 2.27.0`, AWS SDK S3가 있다. (확인, `build.gradle`)
- **인증**
  - JWT(HS256)를 HTTP-only 쿠키 `ACCESS_TOKEN`으로 쓴다. 유효기간 기본값은 24시간이다. (확인)
  - ADMIN 토큰에는 자격 증명 stamp가 들어간다(`AdminSessionValidator`). 관리자 비밀번호를 바꾸면 이전 ADMIN 세션은 모두 무효가 된다. (확인)
  - 임시 비밀번호로 로그인하면 `pwc` claim이 붙고 `PasswordChangeGate`가 대부분의 요청을 막는다(V1118). (확인)
  - 네이버 로그인(OAuth2)은 운영 중이다. 강사로 가입하는 방식(`role=tutor`)은 PR #1098에서 아직 검토 중이다. (확인)
- **역할**: `STUDENT / TUTOR / ADMIN / CREATOR`. `/api/admin/**`는 ADMIN만 쓸 수 있다. (확인, `SecurityConfig`)
- **에이전트용 토큰 필터**(사람 로그인 대신 Bearer 토큰으로 인증)
  - 대상: `ChartAgentTokenFilter`, `EasyCollectorAgentTokenFilter`, `NewstartAgentTokenFilter`, `VisualEditorAgentTokenFilter`
  - 공통 규칙: 경로 허용 목록에 있는 요청만 인증한다. 토큰이 틀리면 인증하지 않고 거부한다(fail-closed). 원문 토큰은 기록하지 않는다. (확인)

## 3. 학생부·출결·내신위키·채점

### 3.1 학생 명부가 세 군데로 나뉘어 있다 (가장 중요한 발견)

| 명부 | 테이블·저장소 | 쓰는 기능 | 서로 연결 |
|---|---|---|---|
| F2R 출결부·장부 학생 | `easy_students` | 출결(`easy_attendance_events`), 반, 수업 요일, 장부 | 없음 |
| LMS 학생(로그인 회원) | `users` + `students` | 대화, 오답노트(`wrong_note_entries`), 채점(`student_answers`, `student_scores`), 학습자료 배정 | 없음 |
| 위키 백엔드 학생 | SQLite(위키 백엔드) | 수능영어 오답·약점 DNA(`get_csat_weakness`) | 없음 |

- 이전 결정: 학생 기준은 F2R 장부(`easy_students`)로 한다.
- 다만 오답과 채점 기록은 LMS와 위키 백엔드에 있다.
- 그래서 「김민수 오답으로 복습 문제」를 하려면 **세 명부를 잇는 연결이 필요하다.** 지금은 이름으로만 이어 볼 수 있어 동명이인 위험이 있다.

### 3.2 출결 (재사용 가능)

- **서비스**: `EasyAttendanceService`. 확인한 메서드는 다음과 같다.
  - 기록: `record(businessId, RecordForm)`. 구분은 `CHECK_IN / CHECK_OUT / ABSENT / LATE / EARLY_LEAVE`이다.
  - 정정: `correct(...)`. 사유가 필수이고, 이전 기록은 남긴다.
  - 조회: `studentHistory(...)`, `sheet(...)`, `studentOverview(...)`
- **API**: `/api/admin/easy/attendance/events` 등이 있다(ADMIN). (확인)
- **주의할 점**
  - `record()`는 중복 기록을 막지 않는다. 같은 학생·같은 날 `CHECK_IN`이 두 번 들어갈 수 있다. Coordinator가 기록 전에 확인하거나, 중복 방지 키를 따로 둬야 한다.
  - 기록 출처(`Source`)는 지금 `MANUAL`과 `ROSTER_IMPORT` 두 가지뿐이다.
  - 학생 이름 검색 API는 없다. `EasyStudentRepository`에는 전체 목록과 상태별 목록만 있다.

### 3.3 내신위키·문항

- **위키 백엔드 MCP**(`problem-generator/wiki-backend-src/backend/mcp_server.py`): `get_final_context`, `get_jikbo_context`, `get_csat_weakness`, `get_csat_problem_context`, `save_generated_questions`, `save_generated_test`, `save_final`, `save_jikbo`, `register_weekly_exam_draft` (확인)
- **AI가 만든 결과물의 검토 상태**: `AI_PROPOSED / TEACHER_CONFIRMED / TEACHER_EDITED / REJECTED`. 검토 대기 구조가 이미 있다. (확인, `main.py` 383행)
- **인증**: 위키 MCP는 `BUILDIT_MCP_API_KEY` 하나로 인증한다. 사용자별 권한은 없다. (확인)
- **촬영본 HTML의 저장 위치는 정해지지 않았다.** 후보는 두 가지이며, 미확인 사항이다.
  - 위키 백엔드의 자료 저장소
  - AI AGENT 제작소: 버전 관리와 사람 승인 기능이 있다(V1096 `studio_reviews`, `studio_tasks`).

### 3.4 채점·오답

- **오답**: `student_answers`(LMS 학생 기준 `is_correct`), `wrong_note_entries`(대화별 업로드), `WrongAnswerService.analyzeForConversation`이 있다. (확인)
- **배정**: 학습자료 배정은 `/api/conversations/{id}/learning-materials`(LMS 대화 기준)로 한다. (확인)

## 4. 기존 MCP 서버와 연결 도구

- **Spring MCP**: `POST /mcp`(JSON-RPC)와 OAuth 2.1(`/mcp/oauth/*`, `/.well-known/oauth-authorization-server`). 동의(grant)는 `mcp_grants`에 저장한다. (확인)
- **권한 범위(scope)**
  - 읽기: `catalog:read`, `studio:read`, `academy:read`, `easy:read`, `wiki:read`, `members:read` 등
  - 쓰기(별도 동의 필요): `studio:propose`, `academy-move:write`, `student-roster:read`, `easy-collect:write`, `cafe:write`, `parent-notice:draft`
- **도구 목록**: `list_catalog`, `read_<서비스>`(목록에 등록된 GET API를 읽기 전용으로 호출), `search`, `fetch`, 제작소 도구 9종, `read_academy_move`, `read_student_roster`, `read_admin_dashboard`, `easy_collection_sites`, `cafe_status`, `cafe_publication_history`, `save_parent_notice_draft` (확인)
- **안전장치**
  - `McpReadOnlyTransactionFilter`: MCP를 통한 읽기 요청은 읽기 전용 트랜잭션으로만 실행된다.
  - 요청 제한: `McpRateLimit`, `McpUserLimiter`
  - 민감 정보 가림: `McpRedaction` (확인)
- **운영 상태(실측, 2026-11-19)**
  - 운영 MCP는 켜져 있고 scope 9개를 공개한다.
  - 이 계정의 Claude 커넥터로 11/18~11/19 사이 약 100건이 호출됐다(감사 로그 `activity_history`).
  - ChatGPT 개발자 모드 커넥터로 쓰기 도구를 쓴 기록이 있다(`docs/studio-collaboration/CHATGPT_STUDIO_CONNECTION.md`).
  - PC Claude Code CLI의 OAuth 연결(localhost callback)은 아직 허용 목록에 없다.
- **학부모 안내문**: `save_parent_notice_draft` → `/dashboard/parent-notices`에 초안을 저장한다. 원장이 카카오톡을 직접 열어 받는 사람을 고른다. **기능 5의 서버 쪽이 이미 있다.** (확인)

## 5. PC의 Claude Code·Codex 실행 환경

- **사용자 PC는 직접 확인하지 못했다.** 이 조사 세션에는 사용자 PC가 연결돼 있지 않다. 설치 여부, 버전, 로그인 방식은 2단계 시작 때 `claude --version`, `claude auth status`, `codex --version`, `codex login status`로 확인한다. (미확인)
- **공식 문서 확인 결과**(2026-11-19)는 다음과 같다. 출처는 `F2R_Coordinator_Architecture.md` §6에 모았다.

| 항목 | Claude Code | Codex CLI |
|---|---|---|
| 비대화 실행 | `claude -p … --output-format json`(결과·`session_id`·`total_cost_usd` 포함) | `codex exec … --json`, `-o 결과파일`, `--output-schema` |
| 이미지 입력 | 전용 옵션 없음. 프롬프트에 이미지 파일 경로를 적고 Read 도구로 읽게 한다. | `-i/--image 파일` |
| 작업 폴더 | 프로세스 실행 위치(cwd)를 작업 폴더로 지정하고, `--add-dir`로 추가 | `-C/--cd`, `--skip-git-repo-check` |
| 권한 | `--allowedTools`, `--permission-mode`, `--max-turns`, `--max-budget-usd` | `-s read-only / workspace-write`. exec에서는 승인을 묻지 않는다. |
| 구독으로 실행 | Pro/Max 로그인 상태면 구독 한도를 쓴다. 무인 실행용으로 `claude setup-token`(1년짜리 토큰)이 있다. | ChatGPT 로그인(`auth.json`) 시 플랜 한도를 쓴다. OpenAI는 이 방식을 "신뢰된 비공개 실행기에서만 쓰는 고급 용도"로 안내한다. |
| API 과금 | `ANTHROPIC_API_KEY`가 있으면 **`-p`에서는 항상 API 키가 우선**이고 토큰 단위로 과금된다. | API 키(`CODEX_API_KEY`)를 쓰면 토큰 단위로 과금된다. 자동화에는 API 키를 권장한다. |
| 정책 | 구독 로그인은 "구매자 본인의 일반적인 Claude Code 사용"용이다. 제품이나 서비스를 만들 때는 API 키를 쓰라고 안내한다. | 자동화에는 API 키를 권장한다. ChatGPT 로그인 자동화는 신뢰된 비공개 환경에서만 쓰라고 한다. |
| MCP | `claude mcp add --transport http`, `--mcp-config`. **헤드리스(`-p`)에서는 OAuth 로그인을 진행할 수 없다.** 미리 로그인해 두거나 정적 헤더 또는 `headersHelper`를 쓴다. | `config.toml`의 `[mcp_servers.x]`에 `url`, `bearer_token_env_var`를 적는다. OAuth는 `codex mcp login`. |
| Windows | 기본 지원. 다만 샌드박스는 WSL2에서만 된다. | 기본 지원. Windows 자체 샌드박스가 기본값이다. |
| 상주 실행 | 공식 서비스 모드가 없다. 감시하는 프로그램이 직접 실행해야 한다. | 같음 |

- **Remote Control**(Claude 앱 → PC의 Claude Code): Android에서 지원된다. 다만 다음 제약이 있다.
  - 사람이 직접 조작하는 창일 뿐, 서버가 작업을 내려보내는 API가 아니다.
  - API 키로는 쓸 수 없다.
  - PC가 켜져 있고 `claude` 프로세스가 계속 실행 중이어야 한다.
  - 그래서 **이 설계의 작업 전달 경로를 대신하지 못한다.** 보조 수단(사람이 직접 지켜볼 때)으로만 쓸 수 있다. (문서)

## 6. 로그인 세션과 사용자 권한

- **웹 세션**: JWT 쿠키이고 유효기간은 24시간이다. 앱이 이 쿠키를 오래 보관하는 방식은 적합하지 않다.
- **기기별 토큰의 선례**: `easy_local_collectors`
  - 기기마다 토큰을 따로 만들고, DB에는 SHA-256 해시만 둔다. 원문은 등록할 때 한 번만 보여 준다.
  - 상태는 `ACTIVE / REVOKED`이다.
  - V997, `EasyLocalCollectorService`, `EasyCollectorAgentTokenFilter` (확인)
- **사업체 접근 권한**: `EasyTutorAccessService.allowedBusinesses / requireAllowedBusiness`로 사업체 단위 접근을 검사한다. (확인)

## 7. 파일 저장·작업 큐·알림

| 구분 | 기존 구현 | 재사용 판단 |
|---|---|---|
| PC 에이전트 작업 큐 | `easy_collector_tasks`: 수집기가 폴링으로 일을 가져간다(heartbeat, next-task, 상태 보고) | **구조를 그대로 본뜬다**(테이블은 새로 만든다. 의미가 다르기 때문) |
| 서버 내부 작업 | `publishing_jobs`(`QUEUED…`), `PublishingWorker` | 상태 머신 참고 |
| 외부 작업자 | 인스타 GPU 작업자: 토큰 하나로 HTTP 폴링 → 결과 업로드 | 같은 모델 |
| 승인 흐름 | AI 업무 비서 `unified_assistant_drafts`·`unified_assistant_audit`: 초안 → `planHash` 승인 → 실행 → 다시 읽어서 확인 | **업무 실행 승인에 재사용** |
| 파일 | S3. `agentmedia`, 제작소 업로드 티켓(`McpStudioUploadTickets`) | 사진·결과 HTML 보관에 재사용 |
| 알림 | 이메일(`ExamIntakeNotificationDelivery`, JavaMailSender), NewStart 알림 | 앱 푸시(FCM)는 **없다**. V1은 앱에서 상태를 주기적으로 조회한다. |
| 로컬 PC 프로그램 선례 | `local-collector/`(Node.js, Playwright): 외부로 나가는 연결만 쓰고 포트를 열지 않는다. 비밀값을 저장하지 않는다. | **PC Agent의 기반으로 삼는다** |

## 8. 기능별 재사용·신규 판단 요약

| 요청 기능 | 그대로 재사용 | 새로 필요(최소) |
|---|---|---|
| 모바일 인증·자동 로그인 | `/api/auth/login`, JWT, 역할 | 기기 등록 + 기기 토큰(collector 방식) |
| PC 등록·연결 | collector 방식(해시 토큰, heartbeat, 폴링) | `coord_devices` 1개 테이블 |
| 작업 큐·상태·로그 | publishing·collector 상태 머신 | `coord_jobs`, `coord_job_events` |
| 승인 | AI 업무 비서 초안·승인, 제작소 리뷰 | 작업별 승인 연결 |
| 출결 | `EasyAttendanceService.record` | 이름 검색 + 동명이인 확인 + 중복 방지 |
| 학습지 HTML | S3, 제작소 또는 위키 저장소(검토 대기) | 저장 위치 결정, 업로드 API |
| 복습 문제 | 위키 MCP 오답 조회·문항 저장(AI_PROPOSED) | **학생 명부 연결**, 검증 단계 |
| 학부모 카톡 | `save_parent_notice_draft`, `/dashboard/parent-notices` | Android 공유 인텐트 |
| MCP 도구 | 기존 게이트웨이·OAuth·scope | `f2r_*` 도구 10종(일부는 기존 도구로 대응) |

## 9. 확인이 필요한 사항 (다음 단계 전 결정)

1. 지정 PC와 실행 환경(Claude Code/Codex 버전, 로그인 방식)
2. **구독 로그인과 API 키 중 무엇을 쓸지.** 정책 확인이 필요하다(설계서 §6 참고).
3. 학습지 HTML 저장 위치: 위키 백엔드인지 AI AGENT 제작소인지
4. 학생 명부 연결 방식: `easy_students`를 기준으로 LMS·위키 학생과 이을 연결 테이블
5. Android 배포 방식: 직접 설치(APK)인지 Play 내부 테스트인지
6. 사용자 범위: V1은 ADMIN(원장) 1인만 쓸지
