# F2R AI Coordinator V1 — MCP 도구 명세

> 상태: **설계안(미구현).** 기존 Spring MCP 게이트웨이(`POST /mcp`, `McpGateway`)에 추가한다.
> 새 MCP 서버는 만들지 않는다. 기존 OAuth·scope·요청 제한·민감 정보 가림(`McpRateLimit`, `McpRedaction`)을 그대로 쓴다.

## 1. 공통 규칙

- **인증**
  - 사람이 쓰는 MCP 클라이언트는 기존 OAuth 동의(grant)를 쓴다.
  - PC Agent가 실행하는 작업은 **그 작업 전용 짧은 토큰**(15분, 작업 1건 범위)을 쓴다. 이 토큰으로는 해당 작업의 사업체와 학생만 다룰 수 있다.
- **새 scope**(기존 목록에 추가, 별도 동의가 필요하다)
  - `coord:read`: 학생 검색, 출결 조회, 문항 검색, 작업 상태
  - `coord:draft`: 초안 만들기(출결 기록 요청, HTML·문항·안내문 초안)
  - **바로 실행하는 scope는 없다.** 모든 쓰기는 초안으로 남고, 사람이 앱이나 웹에서 승인해야 서버가 실행한다.
- **역할**: ADMIN, TUTOR. 사업체 권한(`EasyTutorAccessService.requireAllowedBusiness`)을 검사한다.
- **개인정보**
  - 응답에 실명·연락처·학교를 넣지 않는다. 학생은 `alias`(S01…)와 `studentRef`(서버가 발급한 불투명 ID)로 표시한다.
  - 예외: `f2r_student_search`는 앱의 확인 화면용이다. 이 응답은 AI 실행 토큰에는 가명만, 앱 세션에는 실명을 준다.
- **중복 방지**: 쓰기 도구는 모두 `requestId`(UUID)가 필수다. 같은 요청을 다시 보내면 같은 결과를 돌려준다.
- **금지**: 임의 SQL, 직접 DB 쓰기, 외부 발송, 승인 없는 게시.

## 2. 도구 목록

| 도구 | scope | 읽기/초안 | 기존 기능 재사용 | 신규 |
|---|---|---|---|---|
| `f2r_student_search` | coord:read | 읽기 | `EasyStudentRepository`, 반 배정(`easy_class_members`) | 이름 검색(재원생), 동명이인 목록 |
| `f2r_attendance_check` | coord:read | 읽기 | `EasyAttendanceService.studentHistory`, `sheet` | 오늘 기록 요약 |
| `f2r_attendance_mark` | coord:draft | **초안** | 승인 뒤 `EasyAttendanceService.record` | 같은 날 같은 구분이면 중복 경고, 출결 초안 |
| `f2r_document_upload` | coord:draft | 초안 | S3, `coord_job_files` | 일회용 업로드 URL |
| `f2r_document_to_html` | coord:draft | 초안 | jsoup 정화(`AdmissionsArticleService` 방식), 제작소 또는 위키 검토 대기 | HTML 초안 저장 |
| `f2r_question_search` | coord:read | 읽기 | 위키 MCP `get_csat_problem_context`, `get_final_context` | 위키 백엔드 호출 중계(가명화) |
| `f2r_review_generate` | coord:draft | 초안 | 위키 `get_csat_weakness` | 생성 작업 접수(PC Runner에서 실행) |
| `f2r_review_submit` | coord:draft | 초안 | 위키 `save_generated_questions`(`AI_PROPOSED`) | 검증 결과 첨부 |
| `f2r_parent_message_draft` | coord:draft(또는 기존 `parent-notice:draft`) | 초안 | **기존 `save_parent_notice_draft`를 그대로 씀** | 이름만 연결(별칭) |
| `f2r_job_status` | coord:read | 읽기 | — | `coord_jobs` 조회 |

## 3. 도구별 입력·출력

### f2r_student_search
- **입력**: `{ "query": "김민수", "businessId": 1 }`
- **출력**: `{ "matches": [{ "studentRef": "st_8f2…", "alias": "S07", "grade": 2, "classes": ["화목 중2"], "todaySchedule": "16:00" }], "duplicateName": false }`
- 2명 이상이면 `duplicateName: true`. 그때 앱은 학교·학년을 보여 주고 사용자가 고르게 한다.

### f2r_attendance_check
- **입력**: `{ "studentRef": "...", "date": "2026-11-19" }`
- **출력**: `{ "events": [{ "type": "CHECK_IN", "at": "16:05", "source": "MANUAL" }] }`

### f2r_attendance_mark
- **입력**: `{ "requestId": "uuid", "studentRef": "...", "eventType": "CHECK_IN", "occurredAt": null, "classId": null }`
- **출력**: `{ "jobId": "...", "status": "review_required", "plan": { "student": "S07", "eventType": "CHECK_IN", "at": "16:05" }, "planHash": "…", "warning": null }`
- 오늘 이미 `CHECK_IN`이 있으면 `warning: "ALREADY_CHECKED_IN"`을 돌려준다. 이때 앱은 승인 버튼을 감춘다.
- 실제 기록은 앱에서 승인한 뒤 서버가 한다. 서버가 `record` 결과를 다시 읽어 확인한 뒤 `completed`로 바꾼다.

### f2r_document_upload
- **입력**: `{ "requestId": "uuid", "contentType": "image/jpeg", "bytes": 3500000, "sha256": "…" }`
- **출력**: `{ "fileId": "...", "uploadUrl": "(5분 일회용)" }`
- 25MB 이하, JPEG/PNG만 받는다.

### f2r_document_to_html
- **입력**: `{ "requestId": "uuid", "fileIds": ["..."], "html": "<article>…</article>", "uncertain": [{ "selector": "#q3", "reason": "필기 판독 불확실" }] }`
- **출력**: `{ "draftId": "...", "status": "review_required", "previewUrl": "/coord/jobs/…/preview" }`
- 서버는 HTML을 정화한다. 허용 태그 목록 방식으로 스크립트·이벤트 속성·외부 링크를 지운다. 게시는 승인 뒤에만 한다.

### f2r_question_search
- **입력**: `{ "studentRef": "...", "limit": 20 }` 또는 `{ "school": "...", "grade": "...", "exam": "..." }`
- **출력**: 오답 문항(문항 ID·유형·원문·정답·채점 근거). 학생은 가명으로 표시한다.

### f2r_review_generate
- **입력**: `{ "requestId": "uuid", "studentRef": "...", "count": 5, "runner": "CODEX" }`
- **출력**: `{ "jobId": "...", "status": "queued" }`
- 생성과 검증은 서로 다른 실행으로 나눈다(§아키텍처 7-4).

### f2r_review_submit
- **입력**: `{ "requestId": "uuid", "jobId": "...", "questions": [{ "stem": "...", "choices": ["..."], "answer": "3", "explanation": "...", "sourceQuestionId": "..." }], "verification": { "passed": true, "issues": [] } }`
- **출력**: `{ "wikiStatus": "AI_PROPOSED", "status": "review_required" }`

### f2r_parent_message_draft
- **입력**: 기존 `save_parent_notice_draft`와 같다(`requestId`, `kind`, `title`, `body` 200자).
- **출력**: `{ "draftId": "...", "status": "DRAFT" }`
- 연락처·실명을 넣지 않는다. 보내기는 앱에서 사용자가 카카오톡 공유로 직접 한다.

### f2r_job_status
- **입력**: `{ "jobId": "..." }`
- **출력**: `{ "status": "running", "updatedAt": "…", "events": [ … 최근 10건 ] }`

## 4. 기존 도구와의 관계

- `read_admin_dashboard`, `read_student_roster`는 그대로 둔다. 모두 가명 집계다.
- 위키 백엔드 MCP(별도 서버, API 키 1개 인증)는 **PC의 AI에게 직접 연결하지 않는다.** Spring이 사용자 권한을 확인한 뒤 중계한다.
  - 이유: 위키 MCP는 사용자별 권한이 없다. 키 하나로 전체 자료에 접근할 수 있다.
- 단위 테스트와 계약 테스트는 기존 `McpAdminDashboardTest` 형식을 따른다(given/when/then). 확인할 것은 scope가 없으면 도구가 보이지 않는지, 가명화가 되는지, 중복 requestId를 처리하는지다.
