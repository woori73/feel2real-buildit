# F2R Mobile MCP 도구 명세

> 상태: 설계안(미구현). 기존 MCP 게이트웨이(`/mcp`)에 도구만 추가한다. 휴대폰에서 MCP 서버를 실행하지 않는다.

## 1. 원칙

- **카메라·마이크·스피커·카카오톡은 Android 앱이 직접 다룬다.** MCP의 `mobile_*` 도구는 그 기능을 **요청하는 인터페이스**일 뿐이다.
- AI가 `mobile_*` 도구를 부르면 서버에 **기기 요청**(`mobile_device_requests`)이 생긴다. 이후 흐름은 이렇다.
  1. 앱이 요청을 화면에 띄운다.
  2. **사람이 누르면** 실행되고, 결과가 AI에게 돌아간다.
  3. 응답이 없으면 5분 뒤 `EXPIRED`가 된다.
- 운영체제 권한(카메라·마이크)은 앱이 Android 표준 방식으로 처음 쓸 때 요청한다. 권한이나 사용자 확인을 우회하지 않는다.
- **응답 방식**: 도구는 요청을 만든 직후 `requestId`를 돌려준다. AI는 `mobile_job_status` 또는 `wait=true`(최대 60초)로 결과를 받는다. 비동기 방식이라 MCP 요청이 오래 걸리지 않는다.
- **scope**: `mobile:request`(별도 동의). ADMIN·TUTOR만 쓴다. 그 사용자가 등록한 **활성 휴대폰이 있어야** 한다.

## 2. 모바일 전용 도구

| 도구 | 앱에서 일어나는 일 | 입력 | 출력 | 사람 동작 필요 |
|---|---|---|---|---|
| `mobile_camera_capture` | 카메라 화면을 띄운다: "AI가 학습지 촬영을 요청합니다". 테두리·초점·흔들림 점검 → 촬영 → 미리보기 → [보내기]/[다시 찍기] | `{requestId, jobId, purpose, hint?}` | `{deviceRequestId, status}` → 완료 시 `{fileId, width, height, sha256}` | 예(촬영·보내기) |
| `mobile_image_upload` | 이미 찍은 사진이나 갤러리 사진을 업로드한다(앱이 먼저 찍어 둔 경우 그 사진을 작업에 연결) | `{requestId, jobId}` 또는 앱 내부 호출 | `{fileId}` | 예(고르기) |
| `mobile_voice_command` | ① 앱이 먼저 쓸 때: 받아 적은 명령을 작업으로 접수(앱 기능, AI 호출 아님) ② AI가 물을 때: 질문을 읽어 주고 사용자의 음성 답을 받아 적어 돌려준다 | ② `{requestId, jobId, question}` | ② `{text, confidence}` | 예(말하기) |
| `mobile_speak` | 결과 문장을 휴대폰에서 읽어 준다(음성 응답이 켜져 있을 때) | `{jobId, text(300자 이하)}` | `{status: "SPOKEN" | "MUTED"}` | 아니오(설정을 따름) |
| `mobile_request_approval` | 승인 화면: 작업 요약, 실행 계획, 첨부(사진·HTML 미리보기), [승인]/[거절]/[수정 요청] | `{requestId, jobId, plan, planHash}` | `{decision: "APPROVED" | "REJECTED" | "REVISE", comment?}` | 예(누르기) |
| `mobile_kakao_share` | 승인된 안내문으로 Android 공유 시트를 연다(카카오톡 우선). **받는 사람 선택과 보내기는 사용자가 카카오톡에서 한다** | `{requestId, draftId}`(승인된 안내문만) | `{status: "SHARE_SHEET_OPENED" | "CANCELLED"}` | 예(공유·보내기) |
| `mobile_job_status` | 휴대폰 작업 목록에 상태를 표시한다. AI가 조회할 수도 있다 | `{jobId}` 또는 `{deviceRequestId}` | `{status, updatedAt, lastEvent}` | 아니오 |

**규칙**
- `mobile_kakao_share`는 `parent_notice_drafts`에서 **승인된** 초안만 받는다. 본문을 바로 넘길 수 없다.
- `mobile_request_approval`의 결과는 서버가 **기기 토큰과 요청 ID로 확인한 사람의 응답**만 받는다. AI가 다른 도구로 승인을 만들 수 없다.

## 3. PC·모바일 공통 업무 도구 (기존 우선)

| 업무 | 도구 | 기존 재사용 | 신규 |
|---|---|---|---|
| 학생 검색 | `f2r_student_search` | `EasyStudentRepository`, 반 배정 | 이름 검색, 동명이인 표시, 가명 응답 |
| 출결 조회·등록 | `f2r_attendance_check`, `f2r_attendance_mark`(초안) | `EasyAttendanceService.studentHistory` / `record` | 같은 날 중복 검사, 승인 후 실행 |
| 학습지 등록 | `f2r_document_upload` | S3, 제작소 업로드 티켓(`begin_studio_upload`) 방식 | 작업 연결 |
| HTML 변환 | `f2r_document_to_html`(초안) | jsoup 정화(입시 글 방식), 제작소 검토 또는 위키 | 원본 대조 화면 |
| 내신위키 검색·등록 | `f2r_question_search`, 기존 위키 MCP(`get_final_context`, `save_generated_questions`) | 위키 백엔드 | Spring에서 권한 검사 후 중계 |
| 오답 분석 | `f2r_question_search`(오답 모드) | 위키 `get_csat_weakness`, LMS `student_answers` | 학생 명부 연결(`easy_student_links`) |
| 복습 문제 생성 | `f2r_review_generate`(작업 접수), `f2r_review_submit`(AI_PROPOSED) | 위키 `save_generated_questions` | 생성·검증 분리 |
| 학부모 안내 | `save_parent_notice_draft`(기존 그대로) | 운영 중 | — |
| 작업 결과 조회 | `f2r_job_status` | — | `coord_jobs` |

- 입력·출력 형식은 [../F2R_MCP_Tools.md](../F2R_MCP_Tools.md) §3을 따른다.
- 모바일 앱(REST)과 AI(MCP)는 **같은 서비스 함수**를 부른다. 경로만 다르다.
  - 앱: `/api/mobile/students?query=`
  - AI: `f2r_student_search`

## 4. 오류 응답 (공통)

| 코드 | 뜻 | AI·앱 처리 |
|---|---|---|
| `NO_ACTIVE_DEVICE` | 등록된 휴대폰이 없거나 해지됨 | 앱 등록 안내 |
| `DEVICE_OFFLINE` | 앱이 최근 2분 동안 응답하지 않음(푸시가 없을 때) | 요청은 대기시키고, 사용자에게 앱을 열라고 안내 |
| `REQUEST_EXPIRED` | 5분 동안 사람이 응답하지 않음 | 다시 요청할지 사용자에게 물음 |
| `DECLINED` | 사람이 거절·취소함 | 작업 중단 또는 수정 |
| `SCOPE_REQUIRED` | `mobile:request` 등 동의 없음 | 연결 설정에서 동의 |
| `FORBIDDEN_BUSINESS` | 다른 사업체 자료 | 중단 |
| `DUPLICATE_REQUEST` | 같은 `requestId` | 처음 결과를 다시 돌려줌 |
| `NOT_APPROVED` | 승인 안 된 초안을 공유·게시하려 함 | 승인 요청 먼저 |
| `RATE_LIMITED` | 작업당 기기 요청 10건 초과 | 중단 |
