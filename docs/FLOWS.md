# 기능별 플로우 (Feature Flows)

> 화면·API·상태 흐름. 도메인 스키마: [DOMAIN.md](DOMAIN.md) · API 계약: [API.md](API.md)

## 5. 기능별 플로우

> **§5.0** 은 1차 작업의 핵심 플로우. **§5.1 ~ §5.5**는 현재 구현된 SSR/API 흐름과 후속 확장 포인트를 함께 기록한다.

### 5.0 ⭐ [1차 작업] 자료 업로드 + LLM 분석 + 저장 플로우

학생은 한 번에 **시험지 + 일반 자료**를 섞어서 올릴 수 있다.
일반 자료는 즉시 처리해서 "저장 완료"를 알리고, 시험지는 **백그라운드 분석 → 학생 검증 페이지(메타+채점 한 화면)** 순으로 진행한다.

> **📍 현재 구현 상태**
>
> | 단계 | 구현 상태 |
> |---|:-:|
> | 1단계 (업로드 + 폼 입력 + 묶음 비동기 트리거) | ✅ 구현됨 |
> | 2단계 (학생 폼 기반 contentKind 분류 — LLM 0회) | ✅ 구현됨 |
> | 3단계 (GENERAL 즉시 완료) | ✅ 구현됨 |
> | 4단계 메타 합성 (학생 폼 + LLM 유추) + exams dedup/INSERT | ✅ 구현됨 |
> | 4단계 questions 추출 + 채점 흔적 판별 → EXAM_GRADED promote (`analysis_result` 저장) | ✅ 구현됨 |
> | 4단계 questions.category_id + passage_id 자동 분류/저장 (passages 마스터 INSERT 포함) | ✅ 구현됨 |
> | 4단계 questions.point 자동 추출 ("(3점)" 표기 → 0 default) | ✅ 구현됨 |
> | 5단계 학생 검증 페이지 (메타 + 채점 + 배점 수정 한 화면) | ✅ 구현됨 |
> | 6단계 학생 확정 → 메타 update + `student_answers` / `student_scores`(점수 합산 포함) INSERT | ✅ 구현됨 |
> | 7단계 대화 metadata 동기화 (`exam_score` propagate, 점수+정답률 형식) | ✅ 구현됨 |

**완료된 플로우 요약** (상세 구현 노트: [`docs/archive/phase1-flow-detail.md`](archive/phase1-flow-detail.md))

| 단계 | 핵심 동작 | 담당 서비스 |
|------|----------|------------|
| 1단계 | `POST /api/sources` multipart → sources INSERT → `analyzeBatchAsync` 트리거 | `SourceService.saveBatch` |
| 2단계 | 학생 폼 라디오(GENERAL/EXAM_BLANK) 선택 — LLM 0회 | 폼 입력 |
| 3단계 | GENERAL → 즉시 COMPLETED | `SourceAnalysisService` |
| 4단계 | EXAM_BLANK → LLM 2회(메타 + questions 추출) → exams dedup → PENDING_REVIEW or COMPLETED | `ExamMetaInferenceService` + `ExamExtractionService` |
| 5단계 | EXAM_GRADED + PENDING_REVIEW → `GET /api/sources/{id}/exam-review` 검증 UI | `ExamReviewService.review` |
| 6단계 | `POST /api/sources/{id}/exam-confirm` → exams 메타 update + student_answers + student_scores INSERT | `ExamReviewService.confirm` |
| 7단계 | 6단계 완료 후 conversations.metadata에 exam_score 부착 | `ExamReviewService.propagateExamScoreToConversations` |

---

### 5.1 회원 가입 / 로그인 + 인증·인가

> **🔐 인증 정책 (구현 완료)**
>
> JWT(HS256) HTTP-only Cookie 기반. 모든 보호 API는 쿠키 + service-layer ownership 가드의 2중 방어로 닫힘.
>
> **1. 토큰 발급 & 전송**
> - `POST /api/auth/login` → 비번 검증 후 JWT를 발급하여 `Set-Cookie: ACCESS_TOKEN=...; HttpOnly; SameSite=Lax; Secure(prd)` 로 응답. 클라이언트는 sessionStorage에 사용자 정보(UI용)만 저장하며 토큰은 JS에서 읽을 수 없다 (XSS 방어).
> - `POST /api/auth/logout` → 동일 쿠키를 `Max-Age=0`으로 덮어 즉시 무효화.
> - `GET /api/auth/me` → 쿠키 기반 세션 복원용. 새 탭/새로고침에서 sessionStorage가 비어있어도 본인 정보 재조회 가능.
> - `POST /api/auth/login-id/find` → 이메일로 아이디 목록 조회. `POST /api/auth/password-reset/request` / `confirm` → 비밀번호 재설정. SMTP 실제 발송은 현재 제품 우선순위에서 제외하고, 운영 포함 임시 모드에서는 아이디+이메일 확인 후 비밀번호를 `reset1234`로 직접 변경한다.
> - 토큰 claim: `sub=userId`, `loginId`, `role`. 만료 24h (`app.jwt.expiration-seconds`).
> - secret은 로컬은 yaml의 default, 운영은 `JWT_SECRET` 환경변수 (EC2의 `/etc/f2r/env`).
>
> **2. 필터 흐름**
> 1. `JwtAuthenticationFilter`(OncePerRequestFilter)가 모든 요청에서 쿠키 파싱 → 검증 → `SecurityContext`에 `AuthenticatedPrincipal(userId, loginId, role)` 설정 + `AuditorContextHolder.set(loginId)`.
> 2. `SecurityConfig`가 정적 리소스/대부분의 SSR 뷰/로그인·회원가입/공개 Academy 조회는 permitAll, `/conversation/chat`·`/conversation/chat.html`은 `ADMIN`/`TUTOR`만 허용, `/api/admin/**`는 `hasRole("ADMIN")`, `/api/tutor/**`는 `hasRole("TUTOR")`, 그 외 `/api/**`는 인증 필요. 인증 실패 시 401 (redirect 없이 `HttpStatusEntryPoint`), 인증된 학생이 AI Chat 화면에 직접 접근하면 `/stdt_dashboard`로 리다이렉트.
> 3. 컨트롤러는 `@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal`로 받음. 쿼리·바디에 `userId`/`studentId`를 받지 않는다 (IDOR 방지).
>
> **3. IDOR 차단 (2중 방어)**
> - **컨트롤러 단**: `AuthenticatedUserResolver.resolveUserId/resolveStudentId(principal, targetOverride)` — 학생은 본인만, ADMIN은 `?targetUserId=` / `?targetStudentId=` 임퍼소네이트 허용, TUTOR는 APPROVED 담당 학생만 target 허용. 학생이 다른 ID를 query에 넣으면 `AccessDeniedException(403)`.
> - **서비스 단**: path-id 기반 호출(`GET /api/conversations/{id}` 등)은 컨트롤러 가드를 지나가도 owner를 못 막으므로, 각 service 진입부에서 `authResolver.requireStudentOwnedConversationAccess/Manage`, `requireTutorOwnedConversationAccess/Manage`, `verifyOwnsSource`를 호출한다.
>
> **4. 프론트 인증 UX / redirect 정책 (`/static/js/api.js`, `/static/js/account_menu.js`)**
> - 일반 로그인은 standalone `/login` 페이지가 아니라 공통 로그인 모달에서 처리한다. `/login`과 `/login.html` 직접 접근은 `/home`으로 302 redirect한다.
> - 로그인 모달의 `계정 찾기` 링크는 `/signin/reset/password`로 이동한다. 이 페이지는 이메일로 아이디를 찾고(`아이디 (학생/강사)` 목록), 아이디+이메일로 비밀번호를 `reset1234` 임시 비밀번호로 재설정한다.
> - `/home`은 로그인 여부와 무관하게 접근 가능한 최종 landing이다. 로그인 사용자가 `/home`에 진입해도 role dashboard로 자동 이동하지 않는다. role dashboard 이동은 account menu의 `홈` 항목으로만 제공한다.
> - 로그인 성공 시 `sessionStorage.currentUser`를 UI 캐시로 갱신하고 현재 URL에 머문다. 토큰은 계속 HTTP-only Cookie로만 보관한다.
> - 회원가입 버튼은 기존 `/register` 페이지로 이동하되 내부 path만 허용하는 `returnUrl`을 전달한다. 가입/자동 로그인 후 안전한 `returnUrl`로 복귀하고, 외부 URL·scheme-relative·backslash·인코딩 우회 값은 `/home`으로 fallback한다.
> - 미로그인 상태의 public account area는 `.account-menu`를 숨기고 `[로그인, 회원가입]`을 노출한다. 로그인 후 account menu는 `[홈, 프로필, 로그아웃]`만 노출한다.
> - account menu 홈: STUDENT `/stdt_dashboard`, TUTOR `/tutor/academy`, ADMIN `/admin/students`. ADMIN 표시 label은 `어드민 홈`.
> - 보호 API의 401 → sessionStorage 비우고 `/home`으로, 403 → "접근 권한이 없습니다" alert 후 role별 fallback으로 이동한다. public 로그인 필요 액션은 alert와 모달 유도만 하고 `/login`으로 이동하지 않는다.
> - 단, public 작성/업로드처럼 현재 입력을 보존해야 하는 액션은 `fetchAuth(..., { redirectOnAuthError: false })`로 401/403을 호출부에서 직접 처리해 alert + 로그인 모달만 띄운다.
> - `fetchAuth` / `fetchJsonAuth` 헬퍼와 account modal/menu helper를 공통으로 로드한다.
>
> **5. ADMIN 임퍼소네이트 (학생 본인과 동일 권한)**
> - `/admin/students.html` → 학생 카드 클릭 → `sessionStorage.impersonatedStudent` 저장 + `/stdt_dashboard?asAdmin=1&studentId=...` 이동.
> - 빨간 배너 노출. **권한은 학생 본인과 동일** — 새 대화, 자료 업로드, 자료 삭제, 채팅 전송 모두 가능 (프론트가 `?targetUserId=` / `?targetStudentId=` 자동 부착, 백엔드가 ADMIN role 확인 후 통과).
> - 미래 보강: 학생 검색 기능 + 별도 ADMIN-only 엔드포인트로 임퍼소네이트 흐름 정리 예정.


> **닉네임 표시 정책**: `users.nickname`은 필수 표시명이며 중복을 허용한다. 기존 사용자는 마이그레이션에서 `login_id`로 초기화한다. 사용자에게 보이는 이름은 기본적으로 `displayName`/`nickname`을 사용하고, ADMIN은 `관리자`로 표시한다. `loginId`는 인증/JWT/audit 내부 식별자로 유지한다.

#### 구현된 인증 엔드포인트
```
POST /api/users/students          → 회원가입 (loginId/password/name/nickname/email/phone/schoolName/grade/parentName/parentPhone)
                                    (인증 불필요)
POST /api/auth/login              → 로그인. body { loginId, password } → Set-Cookie + LoginResponse(userId, loginId, name, nickname, displayName, role)
POST /api/auth/logout             → 쿠키 무효화 (204)
GET  /api/auth/me                 → 현재 세션 사용자 정보 (인증 필요)
POST /api/auth/login-id/find        → 아이디 찾기. body { email } → 200 { email, accounts: [{ loginId, role, roleLabel }] }
POST /api/auth/password-reset/request → 비밀번호 재설정 요청. body { loginId, email } → 200 { email, temporaryPassword } (운영 포함 임시 모드)
POST /api/auth/password-reset/confirm → 토큰 검증 후 비밀번호 변경. body { token, newPassword } → 204
POST /api/users/tutors            → 강사 회원가입 (인증 불필요)
GET  /api/users/students/me       → 학생 본인 프로필 (인증 필요). ADMIN은 ?targetUserId= 로 임퍼소네이트.
```

#### 시드 계정

**운영용 (`db/migration/V2__add_admin_account.sql` — 운영/로컬 양쪽 모두 적용)**

| login_id | password | role | 비고 |
|---|---|---|---|
| `admin-f2r` | `admin-mochi-96` (BCrypt) | ADMIN | EC2 첫 배포 시 자동 INSERT. 비밀번호 회전 시 새 V_파일로 보정 (예: `V3__rotate_admin_password.sql`) + `AdminPasswordHashTest` 동시 갱신. |

**로컬 dev-seed (`db/dev-seed/V900__dev_seed.sql` — 로컬 profile만 적용)**

| login_id | password | role | 학교 / 학년 |
|---|---|---|---|
| `admin` | `admin1234` (BCrypt) | ADMIN | — |
| `student` | `admin1234` (BCrypt) | STUDENT | 현대고등학교 / 2학년 + 더미 시험 4건/대화 1건 |

회원가입 없이 바로 로그인해서 테스트할 수 있다. 운영(prd) profile은 `admin-f2r` 한 계정만 존재.

---

### 5.2 학생 대시보드
```
GET /stdt_dashboard   → 학생 대시보드
  - 내 대화 목록 (conversations)
  - 검증 대기 시험지 (PENDING_REVIEW + EXAM_GRADED인 sources)
```
대시보드의 **[+] 새 대화** 클릭 → `POST /api/conversations`로 새 대화 세션 생성 후 `GET /conversation_dashboard.html?id={conversationId}`로 이동한다.
기존 대화 목록 클릭 → `GET /conversation_dashboard.html?id={conversationId}`로 이동한다.

> `/api/conversations`는 학생형 대화 생성 API다. TUTOR는 생성 403이며, 강사 개인 기록은 `/api/tutor/conversations`를 사용한다.

---

### 5.3 자료 업로드 + LLM 대화

> 모든 API는 JWT 쿠키 인증 필요. **userId/studentId는 쿼리·바디에 받지 않는다** — principal에서 자동 결정. ADMIN은 `?targetUserId=` / `?targetStudentId=`, TUTOR는 APPROVED 담당 학생 target만 허용한다 (§5.1).

```
POST /api/conversations[?targetStudentId=]   → 새 대화 세션 생성
POST /api/conversations/{id}/sources         → 묶음 파일 첨부 + sources INSERT (multipart, contentKind/메타 동봉)
                                                 form: targetUserId(ADMIN 임퍼소네이트 전용)

GET  /api/conversations[?targetStudentId=]   → 내 대화 목록
GET  /api/conversations/{id}                  → 학생형 대화 상세 (메시지 + 첨부 파일). 학생 소유 id만 허용.
POST /api/conversations/{id}/messages[?targetUserId=]  → 학생형 메시지 전송 → Claude API → 응답 저장
GET  /api/conversations/{id}/weakness         → radar용 카테고리별 정답률 (service에서 ownership 가드)

GET  /api/sources/pending-review[?targetUserId=]    → 학생 대시보드 "검증 대기" 카드 목록
GET  /api/sources/{sourceId}/exam-review            → 검증 페이지 데이터 (service에서 ownership 가드)
POST /api/sources/{sourceId}/exam-confirm[?targetUserId=] → 학생 확정 (service에서 ownership 가드)
GET  /api/sources/{sourceId}/file?page=N            → 라이트박스 미리보기 이미지 (controller에서 ownership 가드)
DELETE /api/sources/{sourceId}                       → 자료 영구 삭제 (§5.3.1 참고)

GET  /api/students/{studentId}/score-timeline       → 성적 시계열. 학생이 {studentId}≠본인이면 403.
GET  /api/users/students/me[?targetUserId=]         → 본인 프로필 조회

GET  /api/admin/students                             → ADMIN 전용: 학생 목록 + 활동 요약
```

학생형 legacy `/conversation/*` 화면과 `/api/conversations/{id}/...` API는 tutor-owned id를 403으로 닫는다. TUTOR는 APPROVED 담당 학생의 student-owned 대화를 `targetStudentId`/read guard로 읽을 수 있지만, `POST /api/conversations` 생성과 PATCH/DELETE 관리는 금지된다.

#### 5.3.0 강사 개인 대화 기록

```
GET    /tutor/conversation_dashboard       → tutor/tutor_conversation_dashboard 렌더
GET    /tutor/conversations/{id}           → /tutor/conversation_dashboard?id={id} redirect

POST   /api/tutor/conversations            → 강사 소유 기록 생성
GET    /api/tutor/conversations            → 내 강사 기록 목록
GET    /api/tutor/conversations/{id}        → 상세(student=null, sources=[], lesson fields null)
PATCH  /api/tutor/conversations/{id}        → 기록명 변경
DELETE /api/tutor/conversations/{id}        → 기록 삭제
POST   /api/tutor/conversations/{id}/messages → 메시지 전송 → Claude API → 응답 저장
```

소유권은 `conversations.tutor_user_id`로 판단한다. `TutorConversationController`가 CRUD/message API를, `TutorConversationPageController`가 화면/redirect를 담당한다. 학생 소유 id, 다른 강사의 id, ADMIN은 `/api/tutor/conversations/**`에서 모두 403이다. 화면은 목록 불러오기, 새 기록 생성, 열기, 이름 변경, 삭제, 메시지 전송을 제공한다.

#### 5.3.1 자료 삭제 (cascade)

`DELETE /api/sources/{sourceId}` — 본인 또는 ADMIN 임퍼소네이트만 허용 (`SourceService.delete`).

cascade 순서:
1. `conversation_sources` 매핑 제거 — 이 source가 첨부됐던 모든 대화에서 해제
2. 시험지(`source.exam ≠ null`)인 경우만:
   - `student_scores` (source_id 기반 정확) 제거
   - `student_answers` ((student_id, exam_id) 기반) 제거
3. `sources` row 삭제
4. storage 파일 제거 (`FileStorage.delete` — local/S3 양쪽 구현, best-effort)

`exams` 마스터 / `questions` / `passages`는 다른 학생이 공유할 수 있으므로 **보존**. 프론트는 묶음 카드의 ✕ 버튼 → confirm dialog → 묶음의 각 source_id에 DELETE 발사 → radar/timeline 자동 갱신.

**화면 엔드포인트:**
```
GET /conversation_chat?id={conversationId}       → AI Chat 화면
GET /conversation_dashboard.html?id={conversationId}  → 대화별 대시보드
GET /conversation_materials.html?id={conversationId}  → 첨부 자료 화면
GET /conversation_report.html?id={conversationId}     → 리포트 화면 (placeholder)
```
화면 HTML은 모두 Thymeleaf template으로 렌더링한다. 기존 `.html` URL도 `WebMvcConfig`에서 view name으로 매핑해 직접 정적 파일 서빙에 의존하지 않는다.
AI Chat 화면(`/conversation/chat`, `/conversation/chat.html`)과 대시보드 사이드바의 "AI Chat" 메뉴는 ADMIN/TUTOR 전용이다. 학생은 자료 업로드·리포트·오답노트 화면은 계속 사용할 수 있지만, 직접 AI Chat URL로 접근하면 `/stdt_dashboard`로 리다이렉트된다.

**LLM 컨텍스트 구성:**
```
Claude API 호출 시 전달하는 내용:
  1. conversation_sources에 연결된 파일들 (이미지: base64, PDF: 텍스트 추출)
  2. conversation_messages 전체 (대화 히스토리)
  3. 새 메시지

→ 해당 대화에 첨부된 파일 스코프 안에서만 LLM이 답변
→ 기존 대화 재입장 시 히스토리 + 파일 그대로 이어서 사용
```

---

### 5.4 ⭐ 오답노트 (Conversation Review) — `/conversation/review`

대화별 오답노트 페이지. 그 대화에 첨부된 시험지(들)에서 학생이 틀린 문제를 시험 단위로 묶어 보여준다.
[radar(§5.3 weakness)](#53-자료-업로드--llm-대화)와 짝을 이룬다 — radar는 "어느 카테고리가 약점인지" 한눈에, 본 페이지는 "어떤 문제를 어떻게 틀렸는지" 깊게.

> **📍 현재 구현 상태**
>
> | 단계 | 구현 상태 |
> |---|:-:|
> | 백엔드 — `WrongAnswerService` + `GET /api/conversations/{id}/wrong-answers` + verifyOwnsConversation | ✅ |
> | 프론트 — `/conversation/review` 페이지 + 시험별 그룹 + 카테고리 필터 | ✅ |
> | 사이드바 메뉴 통합 — 기존 4개 conversation_* 페이지에 "오답노트" 메뉴 추가 | ✅ |
> | Admin 임퍼소네이트 — `?asAdmin=1` query 전파 + service-layer ADMIN bypass | ✅ |

**진입점**
- 학생 본인: `/conversation/dashboard?id=...` 등 conversation 내부 페이지 사이드바의 "오답노트" 클릭
- ADMIN 임퍼소네이트: `/conversation/review?id=...&asAdmin=1` (학생 대화에 진입한 상태로 같은 메뉴 사용)

**데이터 흐름**
```
1) URL ?id={conversationId} → ConversationController.detail로 사이드바/학생 정보 채움
2) GET /api/conversations/{id}/wrong-answers
   - service: verifyOwnsConversation → conversation_sources에서 exam 연결된 source의 exam_ids 추림
   - student_answers JOIN questions(JOIN category) WHERE student_id=, exam_id IN (...), is_correct=false ORDER BY exam_id, order_num
   - 시험별 그룹핑 + (exam.display_name / 시험별 총 답안 수 countByStudentAndExams) 매핑
3) 응답: { exams: [{ examId, examDisplayName, wrongCount, totalCount, questions: [{...}] }] }
4) 프론트: 카테고리 칩 필터 — 클릭 시 해당 카테고리 문제만 노출 (서버 재호출 없이 캐시된 결과 필터링)
```

**LLM 호출 없음.** 기존 `student_answers` / `questions` / `question_categories` JOIN만으로 충분 (신규 마이그레이션 X).

**후속 단계 연결**
- Admin/TUTOR가 오답을 보고 학생에게 학습자료(PDF)를 부여하는 흐름은 §5.7에서 처리한다.
- docx 업로드 → 서비스 내 자동 문제 출제/풀이는 §7(확장 계획) 참고.

---

> 진단 / 이메일 / 숙제 풀이는 **후속 단계로 미룸**. 현재는 §5.0 시험지 업로드/분석/검증, §5.4 오답노트, §5.5 Tutor Academy/Lesson Builder, §5.6 학생 수업 검색·자동 신청, §5.7 학습자료 부여·풀이 재업로드를 구현한다. 결제/구독은 §5.6의 후속 범위다.

---


### 5.5 ✅ Tutor Academy + Lesson Builder — `/tutor/*`, `/academies`, `/academies/{slug}`

강사는 학생 영역과 분리된 화면에서 회원가입하고, 본인 Academy를 만든 뒤 수업 상품을 등록한다.

> **📍 현재 구현 상태**
>
> | 단계 | 구현 상태 |
> |---|:-:|
> | 강사 회원가입 (`POST /api/users/tutors`) | ✅ 구현됨 |
> | TUTOR 로그인 후 `/tutor/academy` 이동 | ✅ 구현됨 |
> | Academy 생성/수정/삭제 — 공개주소(handle) 설정/수정, 로고 선택(미입력 시 기본 로고), 커버 선택, 과목/대상/지역 드롭다운/소개 입력. 삭제는 수강생 없을 때만 가능 | ✅ 구현됨 |
> | 공개 Academy 목록(`/academies`) — 과목/대상/지역 필터 + 카드 목록 | ✅ 구현됨 |
> | 공개 Academy 페이지(`/academies/{slug}`) | ✅ 구현됨 |
> | 수업 상품 전용 편집기 — 다중 탭 draft/명시적 미리보기/학교·학년·가격 입력/원자적 일괄 저장 | ✅ 구현됨 |
> | LMS 강좌관리 — owner handle 기준 실제 LessonProduct 목록·soft-delete(Stage 2a) | ✅ 구현됨 |
> | 공개 Academy에 PUBLISHED 수업 카드 노출 | ✅ 구현됨 |
> | 학생 수업 검색/자동 신청/취소 | ✅ 구현됨 |
> | 결제/구독 | 🚧 후속 단계 |

**강사 가입/진입 흐름**

1. `/register?role=tutor`에서 강사 탭이 선택된 상태로 가입한다.
2. 프론트는 가입 성공 후 `/api/auth/login`을 호출해 자동 로그인한다.
3. `Role.TUTOR`는 `/tutor/academy`로 이동한다.
4. Academy가 없으면 생성 CTA를 보여주고, `/builder`에서 생성한다. 기존 Academy가 있으면 `/tutor/academy`의 `Academy 수정`은 `/builder`로 이동한다. `/builder` 진입 시 서버가 JWT principal과 `TUTOR` role을 확인하며, 비로그인 또는 비강사 사용자는 Builder를 렌더하지 않고 랜딩(`/`)으로 리다이렉트한다. 정상 TUTOR의 `/builder`는 기존 Academy 이름/slug/handle/공개 경로를 Thymeleaf SSR 모델로 채우고 canvas에 iframe 없이 공개 페이지 모양 DOM을 직접 렌더한다(주소바 표시는 실제 `{handle|slug}.feel2real.co.kr`). Builder의 섹션 표시 토글은 hero를 항상 ON으로 고정한다. 기존 Academy의 데이터 섹션(`sec-intro`, `sec-principal`, `sec-lessons`, `sec-social`)은 `PUT /api/tutor/academy/page-settings`로 `pageSettings.sectionVisibility`만 갱신한 뒤 Builder preview DOM을 즉시 재렌더하고 클릭한 섹션 위치로 스크롤한다. `sec-reviews`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`는 저장된 legacy 값과 무관하게 항상 OFF이며 클릭/토글·Builder/public 렌더·레이아웃 목록에서 제외한다. Builder 진입만으로 write하지 않고 다음 정상 저장/발행 payload에서 6개 값을 명시적 `false`로 정규화한다. 신규 발행 전 Academy는 같은 토글 값을 sessionStorage pending payload에 반영하고 최초 발행 POST에 포함한다. 레이아웃 탭은 블록 탭에서 ON인 섹션만 보여주고, hero LOCK/index 0을 제외한 섹션을 up/down 버튼으로 재정렬해 `pageSettings.sectionOrder` full order로 저장한다. OFF 섹션은 화면에서 제외되지만 기존 full order slot은 유지해 다시 ON이 되면 같은 위치로 복귀한다. Builder의 텍스트 편집은 section-scoped UX다. 텍스트 target은 최초 `contenteditable="false"`이며, 섹션/field 클릭 시 해당 섹션만 편집 모드가 되고 `block-controls` 형태의 top-right 컨트롤(`적용`, `삭제`)이 섹션 outline 우측 상단에 표시된다. `적용`은 `applySectionTextChanges`로 text-only 변경을 반영하며, `삭제`는 좌측 섹션 토글과 동일하게 sectionVisibility를 OFF로 저장한다(hero 제외). blur는 더 이상 저장/커밋하지 않는다. Enter는 단일행 field에서 suppress되고, multiline field는 `intro`, `introMotto`, `introBodySecondary`만 허용한다. 공개/Builder multiline 렌더는 `academy-multiline-text`/`builder-multiline-text` taxonomy를 사용한다. 신규 발행 전에는 pending payload/sessionStorage에 반영해 최초 POST에 포함하고, 랜딩 월 수강료는 `pageSettings.builderDraft.monthlyTuition`으로 넘겨 히어로 월 수강료 지표에 표시한다. 히어로는 학원명 → 대표 수업 → 대표 교재 → 소개/대상·과목/지표 순으로 렌더하며 지역 eyebrow와 `00 전문` 강조 줄은 표시하지 않는다. 대표 수업은 등록 목록과 자동 동기화하지 않고 Builder에서 고른 최대 4개의 lesson ID만 `builderDraft.featuredLessonIds`에 저장해 공개한다. 히어로 지표 3개(`monthlyTuition`, `scoreImprovement`, `operationYears`)는 Builder에서 클릭 편집해 `builderDraft`로 저장한다. 공개 주소(handle)는 브라우저 URL 영역 클릭으로 수정하고 기존 handle 검증/중복 확인을 거쳐 저장한다. Footer는 `sectionOrder`/`sectionVisibility` 밖 fixed page chrome이다. Footer 또는 특정 값 텍스트를 클릭하면 raw 7개 값만 다른 섹션과 같은 field-scoped `contenteditable` 상태가 되고 footer/값에 노란색 outline, 우상단 Apply 컨트롤이 표시된다. 별도 form/modal은 열지 않는다. 단일행 값은 Enter를 막고 주소만 줄바꿈을 허용하며 Escape는 현재 값만 snapshot raw로 되돌린다. Apply 성공 뒤에만 preview를 갱신하며 validation/PUT/storage 실패 시 draft를 유지한다. Dirty footer에서 section/GNB/canvas 밖을 클릭하면 저장을 즉시 시작하면서 원래 클릭 흐름은 계속하고, saving 중 추가 interaction만 막는다. 신규 Academy는 `pendingAcademyPublish.pageSettings.businessInfo`를 권위 snapshot으로 저장하고 preview cache는 이 값을 우선 merge한다. FTC link는 사업자등록번호 숫자 10자리에서 자동 파생한다. 공개 홈과 해당 Academy 블로그 목록/상세가 같은 footer 값을 사용하며 blank는 숨기지 않고 masked fallback한다. 기존 Academy는 텍스트 적용 후 dirty 상태로 표시한 뒤 상단 `발행`으로 기존 subjectIds/targetGroups/regions/pageSettings와 `pageSettings.builderDraft`를 보존한 `PUT /api/tutor/academy` JSON 저장을 수행한다. 이 UX 변경으로 새 API endpoint/request/response는 추가하지 않는다. 랜딩의 주력 과목은 복수 선택 가능하며 복수 `subjectIds`로 최초 발행 payload에 들어간다. 분류/지역·이미지 등 상세 편집은 builder 상단 `데이터 편집(임시)`를 통해 `/tutor/academy/setup`으로 진입한다. 외부 연동(`sec-social`)은 네이버 블로그 URL과 네이버 지도 URL을 각각 하나씩 Builder 내부 모달에서 등록한다. 저장 성공 후 `/tutor/academy`로 돌아와 수업 상품 생성 버튼을 노출한다. 공개/Builder 페이지의 `sec-lessons`는 등록 수업상품을 편집기와 동일한 색상·타이포의 둥근 연결형 탭으로 노출한다. 초기에는 탭만 보이고, 선택하면 편집기 프리뷰 본문이 즉시 열린다. 열린 상태에서 다른 탭은 내용만 교체하며 같은 탭을 다시 선택하면 즉시 닫힌다. 이 프리뷰에는 신청 CTA를 포함하지 않는다. 수업 상품은 Academy 대상학생과 동일한 `TargetGroup` enum 단일값을 갖고, 기존 수업은 V927에서 `HIGH`로 backfill한다. 커리큘럼은 추후 curriculum 테이블과 academy/lesson 연결 모델이 확정되면 재도입한다.
5. Academy 화면의 수업 상품 생성 버튼과 Builder의 수업 관리 버튼은 모두 `/tutor/academy/lessons/editor`로 이동하며 기존 목록에 신규 draft를 자동 추가하지 않는다. 수업 상세의 수정은 `/tutor/academy/lessons/editor?lessonId={id}`로 이동한다. 편집기 진입 시 서버가 JWT principal과 `TUTOR` role을 확인하며, 비로그인·학생·ADMIN은 편집기를 렌더하지 않고 랜딩(`/`)으로 리다이렉트한다(`/builder`와 동일 규칙). 기존 Academy/수업 상세의 카드 이동, 삭제, 신청/수강생, 커뮤니티 진입은 유지하며 인라인 수업 편집 modal은 사용하지 않는다.
6. 랜딩에서 `/builder`로 이월된 발행 폼은 Builder "발행" 시 `POST /api/tutor/academy`를 **multipart**로 호출한다. `academy` part에는 JSON payload를 담고, 발행 전 업로드한 로고가 있으면 `logo` part에 함께 보낸다. 로고 없는 JSON 생성도 같은 경로/서비스에서 계속 지원한다. 동일 필수값을 보내며 로고는 서버 기본 로고로 채운다. 랜딩은 handle을 받지 않으므로 서버가 `academy-{userId 해시}` 임시 공개주소를 자동 부여하고, 강사는 `/tutor/academy/setup`에서 이후 수정한다.

7. 발행된 공개 페이지(`/academies/{slug}`) 우상단 `상담 신청`은 방문자에게만 보이며(소유 강사에게는 `학원 설정으로`), 클릭하면 랜딩 `문의하기`와 같은 형태의 연락 모달을 연다. 옵션은 해당 학원의 `pageSettings.businessInfo` 중 **강사가 실제로 입력한** `phone`/`email`만이며 각각 `tel:`/`mailto:` 링크가 된다(전화 먼저). footer의 마스킹 dummy(`000-0000-0000`, `academy@example.com`)는 링크로 쓰지 않는다. 둘 다 blank면 모달 대신 기존 데모 안내(`상담 신청 기능은 준비 중입니다.`)를 유지한다 — 상담 접수 저장 백엔드는 아직 없다. 별도 입력 필드/API를 추가하지 않고 Builder footer 사업자 정보 편집을 그대로 재사용한다.

8. 강사는 이 연락처를 `/builder` preview의 `상담 신청` 버튼을 눌러 편집한다. Builder에서 이 버튼은 방문자 모달 대신 `상담 연락처 수정` 모달(전화 1개 + 이메일 local·도메인 select, 마지막 항목 `직접입력`)을 열고, 저장은 footer 사업자 정보와 같은 `pageSettings.businessInfo`(기존 Academy=`PUT /api/tutor/academy/page-settings`, 발행 전=`pendingAcademyPublish`)로 나가므로 **편집 결과가 footer에도 그대로 반영된다**. 전화는 입력 중 자동 포맷하고(서울 `02`=2-3-4/2-4-4, 그 외=3-3-4/3-4-4) 저장 시 `0` 시작 9~11자리만 통과하며, 같은 검증을 footer 인라인 편집도 공유한다. → [academy-builder-page.md](academy-builder-page.md)

**수업상품 전용 편집기 흐름**

1. 편집기는 `GET /api/tutor/academy/me`로 `HIDDEN`을 제외한 모든 수업을 불러온다. Academy가 없어서 `204`이면 `/builder`로 이동한다.
2. `?new=true`는 신규 draft를 마지막 탭으로 추가해 활성화하고, `?lessonId={id}`는 해당 기존 수업을 활성화한다. 선택 ID가 없으면 오류를 표시하며, 수업이 하나도 없으면 신규 draft 하나를 만든다.
3. 편집기는 기존 공개 문구 입력에 학교·학년 자유문구와 가격 amount/unit을 추가한다. 학교·학년은 blank→null이다. 가격 UI의 유효 조합은 `null/null`, `positive/MONTH|COURSE`, `null/NEGOTIABLE`이며 신규 또는 가격을 바꾼 기존 수업은 그 밖의 조합을 네트워크 호출 전에 막는다. 다만 서버에 이미 저장된 `positive/null` 레거시는 로드만으로 dirty가 되지 않고 학교·학년 또는 비가격 필드만 바꿀 때 그대로 round-trip한다. 수업 유형·수강 대상·공개 상태·정원·일정은 폼에 없으며 `status`만 서버가 `PUBLISHED`로 정규화한다.
4. 각 탭은 `serverSnapshot`, 입력 중인 `draft`, 화면에 그려지는 `appliedPreview`를 따로 보관한다. 탭 전환 전에 현재 form을 draft에 캡처하므로 한 화면의 form만 사용해도 탭별 입력이 유지된다.
5. 입력은 즉시 미리보기에 반영된다. 타이핑할 때마다 `draft`를 `appliedPreview`로 옮겨 우측 미리보기와 탭 문구가 함께 갱신되며, 별도의 `미리보기 반영` 단계는 없다. 배경 스타일은 학원 공통이라 한 번 바꾸면 모든 수업의 draft와 미리보기에 같은 값이 적용된다.
6. 마지막 저장은 적용 여부와 무관하게 draft를 기준으로 `PUT /api/tutor/academy/lessons/collection`을 한 번 호출한다. 모든 신규 draft와 서버 snapshot이 달라진 기존 수업만 보내며, 빠진 기존 수업은 변경하지 않는다.
7. 성공하면 반환된 ID/revision/작성 상태로 `serverSnapshot`만 갱신한다. 미리보기는 이미 입력과 같은 상태이므로 별도 갱신이 필요 없다.
8. `LESSON_AUTHORING_STALE`이면 최신 Academy를 다시 읽어 기존 수업의 server snapshot/revision만 rebase한다. 로컬 draft와 applied preview는 유지되며 사용자가 탭별 차이를 검토한 뒤 재저장한다. 숨김/삭제된 대상 또는 최신 조회 실패도 로컬 입력을 지우지 않는다. 다른 검증/409/네트워크 실패도 전체 편집 상태를 유지한다.
9. 탭은 native button으로 렌더하고 활성 탭에 `aria-current`를 둔다. 추가 버튼은 항상 마지막이며 개수 제한은 없다. 추가 버튼도 다른 탭과 같은 `small`(안내) + `strong`(제목) 2줄 구조를 쓴다(`새 수업` / `+ 추가하기`). AI 작성 도움은 실제 `disabled`/`aria-disabled` 상태이고, 샘플·미디어·JSON 편집 기능은 제공하지 않는다.
10. `수업 삭제`는 현재 선택한 수업을 지운다. 확인창을 거친 뒤, 기존 수업은 `DELETE /api/tutor/academy/lessons/{lessonId}`로 숨김 처리하고 저장 전 신규 draft는 탭만 없앤다. 승인된 신청/수강생이 있어 서버가 `409`를 주면 삭제하지 않고 안내만 표시한다. 마지막 하나를 지우면 빈 신규 draft를 자동으로 만든다.
11. 단일행 입력에서 Enter는 폼을 암묵적으로 제출하지 않는다. `textarea`는 줄바꿈을 유지하고, 버튼은 사용자가 의도적으로 활성화한 경우이므로 그대로 통과한다. 저장은 항상 명시적인 `수업상품 저장` 활성화로만 일어난다.

**LMS 강좌관리 Stage 2a 흐름**

1. owner TUTOR가 `/lms/{handle}/manage`에 진입하면 SSR owner guard 이후 `GET /api/tutor/lms/{handle}/lessons`를 호출한다. API도 handle owner를 다시 검증한다.
2. 강좌관리 탭은 DRAFT/PUBLISHED 실제 row를 생성시각/ID 순으로 표시한다. 컬럼은 `학교·학년·강좌명·가격·소개·관리`이며 null 학교·학년/가격은 안전한 `-`로 표시한다. contentEditable과 인라인 저장은 없다.
3. `+ 강좌 등록`은 기존 `/tutor/academy/lessons/editor`로 이동한다. 수정도 전용 편집기에서만 수행한다.
4. 삭제는 기존 hide API를 호출한다. `204`일 때만 row/count를 갱신하고 `409` 또는 다른 실패는 현재 row/count를 유지한다.
5. 이 live read/delete 흐름은 `LmsState`, cart count, review state, sessionStorage, `LMS_MOCK.courses`를 읽거나 변경하지 않는다. 후기 탭과 다른 LMS 화면은 기존 `LmsData.listCourses()` MOCK semantics를 유지한다.

**Academy 생성 필수값**

- 로고 이미지: 선택. 업로드하면 서버에서 안전하게 JPEG로 재인코딩 후 저장하고, 미입력/null이면 서버가 기본 JPEG 로고를 기존 FileStorage 경로로 저장한다.
- 커버 이미지: 선택.
- 공개주소(handle): setup에서는 필수. 3~30자 소문자/숫자/하이픈, 예약어/중복 차단. 랜딩 JSON 생성처럼 비워진 생성 요청은 서버가 임시 handle을 자동 부여한다.
- 이름, 소개(1000자 이하), 과목 1개 이상, 대상 1개 이상, 지역 1~3개.
- 지역은 SSR에서 시/구 드롭다운으로 입력한다. 구/군을 선택하지 않으면 `ALL`로 저장해 “전 지역”을 표현한다.

**수업 상품 생성 필수값**

- 수업명, 구독형/단기형, 가격 단위/표시 문구, 정원, 일정 유형/표시 문구.
- `price_unit=NEGOTIABLE`이면 `price_amount` 생략 가능. 그 외에는 1원 이상 필요.
- `schedule_type=WEEKLY`이면 `weekly_count`와 요일 1개 이상 필요.
- `schedule_type=PERIOD`이면 날짜 범위 또는 기간(주/개월) 중 하나가 필요.

**보안/노출 정책**

- `GET /api/academies`, `GET /api/academies/**`, `/academies`, `/academies/{slug}`는 public. 단 `PUBLISHED` Academy만 조회된다.
- `/api/tutor/**`는 `ROLE_TUTOR` 필요. 서비스에서 `verifyOwnsAcademy`로 소유자도 확인한다.
- public DTO는 `logoUrl`/`coverUrl`만 제공하고 storage key는 노출하지 않는다.
- TUTOR 본인 Academy 삭제는 승인된 신청/수강생이 없는 경우에만 허용한다. 삭제 성공 시 같은 강사는 새 Academy를 다시 만들 수 있다.

**서브도메인 스코핑 (`{handle}.feel2real.co.kr`)**

서브도메인은 **그 학원의 홈페이지 하나만** 담당한다. 공통 경로 앞에 학원 식별자가 붙어서는 안 된다.

| 요청 | 처리 |
|---|---|
| `{handle}.base/` (root) | 학원 공개 페이지 렌더 (`/subdomain-home` forward) |
| `{handle}.base/home`, `/academies`, `/community` 등 공통 경로 | apex 동일 path로 **301** (query 보존) |
| `{handle}.base/api/**`, `/css/**`, `/js/**`, `/img/**`, `/favicon.*`, `/robots.txt` | **통과** — 학원 페이지가 같은 호스트에서 로드하므로 리다이렉트하면 깨진다 |
| apex(`feel2real.co.kr`) 모든 경로 | 필터 미관여 — 기존 랜딩/홈 그대로 |

- 진입 경로는 `/academies`(학원찾기)에서 학원 클릭 → `AcademyPathRedirectInterceptor`가 `{handle}.base/`로 301.
- base-domain 미설정(local/test)이면 위 전부 **no-op**.
- 구현: `SubdomainAcademyFilter`.

**공개 Academy 목록 흐름**

1. 학생 또는 비로그인 사용자가 `/academies`에 진입한다.
2. 프론트는 `GET /api/academies`를 호출해 공개 Academy 카드 목록을 표시한다.
3. 사용자는 `subjectId`, `targetGroup`, `city`, `district`로 필터링할 수 있다.
4. 카드의 "Academy 보기"를 누르면 `/academies/{slug}` 상세 페이지로 이동한다.

**학원 홈페이지 방문 통계 흐름**

1. 공개 Academy 렌더 후 비소유자·비Admin 브라우저는 `POST /api/academies/{slug}/visits`를 호출한다.
2. 서버는 `Asia/Seoul` 날짜와 HTTP 세션을 기준으로 같은 학원의 당일 첫 호출만 일별 counter에 반영한다.
3. 공개 페이지는 응답의 오늘·누적 횟수와 최근 7일(0건 날짜 포함)을 본문 흐름과 분리된 좌측 플로팅 카운터·선형 그래프로 표시한다. 화면이 좁으면 본문을 가리지 않도록 하단 축약형 카운터로 전환한다.
4. 소유 강사와 Admin은 `GET`으로 조회만 해 운영 확인이 방문 수를 부풀리지 않는다.
5. `/admin/academies`는 숨김을 포함한 모든 Academy의 동일 통계를 카드별로 표시한다.

표시 숫자는 영구 고유 사람 수가 아니라 **세션 기준 방문 횟수**다. 원시 IP나 브라우저 식별자는 저장하지
않으며 GA4는 별도로 유입 채널·행동 퍼널 분석을 담당한다.


**Academy 블로그 포스트 모아보기 흐름 (`/academy-posts`)**

1. 비로그인/로그인 사용자가 `/academy-posts`에 진입한다.
2. 프론트는 `GET /api/academy-posts?keyword=&sort=latest|recommended`를 호출한다.
3. 서버는 `PUBLISHED && is_visible=true` 글 중 Academy도 `PUBLISHED && visible=true`인 글만 반환한다.
4. 최신순은 `published_at DESC`, 추천순은 최근 `app.academy-blog.recommendation-window-days`일 좋아요 수 DESC 후 최신순으로 정렬한다.
5. 글 카드를 누르면 기존 상세 URL `/academies/{academySlug}/posts/{postSlug}`로 이동한다.
6. 로그인 사용자는 목록/상세에서 `POST /api/academy-posts/{postId}/like`로 추천을 토글한다. 비로그인 사용자는 추천 클릭 시 로그인으로 유도한다.
7. 추천은 `academy_blog_post_likes`에 사용자별 1회 저장하고 다시 누르면 row를 삭제한다. 추천 가능 기간은 글 발행 후 `app.academy-blog.like-allowed-days`일(기본 30일)이며 운영 환경 설정으로 변경 가능하다.


### 5.6 ✅ 학생 수업 검색 + 자동 신청 — `/lessons`, `/api/lesson-applications`

학생이 공개 Academy의 수업 상품을 검색하고 신청한다. 신청은 강사 승인 없이 정원 검증 후 즉시 승인되며, 신청 시 학습 대화(conversation)가 연결된다 (기존 대화 선택 or 자동 생성). TUTOR의 학생 자료 접근 권한은 `lesson_applications.status=APPROVED` 기반으로 판정한다 (V9 이후).

> **📍 현재 구현 상태**
>
> | 영역 | 구현 상태 |
> |---|:-:|
> | 학생용 수업 검색 페이지(`/lessons`) | ✅ |
> | 공개 검색 API(`/api/lessons/search`) | ✅ |
> | Academy 공개 페이지 수업 신청 CTA | ✅ |
> | 학생 신청 생성 — 정원 여유 시 즉시 APPROVED | ✅ |
> | 신청 시 conversation 연결 (기존 선택 or 자동 생성) | ✅ |
> | 중복 신청 방지 | ✅ |
> | 학생 신청 목록/취소 | ✅ |
> | 취소 시 정원 반환 (conversation 연결 이력 유지) | ✅ |
> | TUTOR 권한 가드 — lesson_applications APPROVED 기반 (tutor_student_map 제거) | ✅ |
> | 강사용 수업별 신청자 목록 (`GET /api/tutor/lessons/{lessonId}/applications`) | ✅ |
> | 결제/구독/강사 승인/거절/채팅/알림/리뷰 | 🚧 후속 또는 제외 |

**신청 상태 머신**
```
CANCELED ─(재신청 + 정원 여유)─▶ APPROVED
APPROVED ─(학생 취소)──────────▶ CANCELED
```

**정원 정책**
- 검색/상세 UI는 `current_count >= max_capacity`이면 신청 버튼을 비활성화한다.
- 서버는 신청 요청 시 `lesson_products` row를 pessimistic lock으로 다시 검증한다.
- 정원이 가득 찬 직접 API 요청은 실패한다.

**권한 정책**
- `GET /api/lessons/search`는 public. 단 `PUBLISHED` Academy + `PUBLISHED` Lesson만 노출한다.
- `POST /api/lesson-applications`, `GET /api/lesson-applications/me`, `DELETE /api/lesson-applications/{id}`는 STUDENT만 사용한다.
- `GET /api/tutor/lesson-applications`는 TUTOR 본인 Academy/Lesson 신청만 조회한다.
- 강사가 전체 학생을 검색하거나 신청 전 학생 성적/오답/학습자료를 보는 기능은 없다.

**API 요약** (자세한 계약은 `docs/API.md` §11 참고)
```
GET    /api/lessons/search                                  공개 수업 검색
POST   /api/lesson-applications                             학생 수업 신청(즉시 승인 + conversation 연결)
GET    /api/lesson-applications/me                          내 신청 목록
DELETE /api/lesson-applications/{id}                        신청 취소
GET    /api/tutor/lesson-applications                       강사 본인 전체 신청/수강생 목록
GET    /api/tutor/lessons/{lessonId}/applications           강사용 수업별 신청자 목록
```

### 5.7 ⭐ 학습자료 부여 + 풀이 재업로드 (Phase 2)

Admin 또는 TUTOR가 학생에게 PDF 학습자료를 부여하고, 학생이 PDF를 다운받아 종이로 풀이한 결과를 사진/PDF로 재업로드하는 흐름.
오답노트(§5.4)에서 약점을 확인한 강사가 곧바로 보충 자료를 학생에게 줄 수 있게 한다.

> **📍 현재 구현 상태**
>
> | 영역 | 구현 상태 |
> |---|:-:|
> | DB 스키마 (V4 tutor_student_map / V5 learning_materials + answers) | ✅ |
> | DB 스키마 (V11 learning_material_feedbacks + HOMEWORK_FEEDBACK source_type) | ✅ |
> | LearningMaterialService (assign/list/download/complete/getAnswerFile/delete) + 10 unit tests | ✅ |
> | LearningMaterialService (addFeedback/getFeedbackFile/deleteFeedback) + 재제출 시 피드백 삭제 | ✅ PR2 |
> | LearningMaterialController 7개 endpoint (PR1) | ✅ |
> | LearningMaterialController 피드백 3개 endpoint (PR2) | ✅ PR2 |
> | UI — 학생: stdt_dashboard "받은 학습자료" 카드 + 풀이 업로드 모달 | ✅ |
> | UI — 강사/Admin: conversation_review에 [+ 학습자료 부여] 버튼 + 모달 + 부여 목록 | ✅ |
> | AuthenticatedUserResolver — verifyAdminOrTutor / verifyOwnsLearningMaterial / verifyOwnsConversation TUTOR 분기 | ✅ |
> | dev-seed V901 — 테스트 TUTOR 계정(`tutor`/`admin1234`) + 학생 매핑 | ✅ |
> | docx 업로드 → 자동 문제 출제 | 🚧 §7 후속 |

**권한 정책**
- **부여(assign)**: ADMIN 또는 TUTOR (TUTOR는 `lesson_applications.status=APPROVED` 담당 학생만 — V9 이후)
- **목록 조회**: 학생 본인 + ADMIN(임퍼소네이트) + TUTOR(APPROVED 담당 학생)
- **다운로드/미리보기**: 학생 본인 + ADMIN + APPROVED 담당 TUTOR
- **풀이 업로드(complete)**: 본인 학생 + ADMIN + APPROVED 담당 TUTOR (실용상 학생만 쓰지만 가드는 동일)
- **삭제**: ADMIN 또는 부여자 본인 (다른 TUTOR가 부여한 자료는 못 지움)

**파일 저장 방침**
- 문제 PDF: `FileStorage.saveRaw`로 raw 보관 → 학생이 그대로 PDF 다운로드 가능 (`file_type=UNKNOWN`).
- 학생 풀이 파일(이미지/PDF): 마찬가지로 raw 보관. 미리보기 Content-Type은 원본 확장자로 추론(PDF → `application/pdf`, JPG → `image/jpeg` 등).
- 둘 다 `content_kind=GENERAL`, `processing_status=COMPLETED`로 INSERT — LLM 분석 워크플로 우회 (분석 대상 아님).

**상태 머신** (`learning_materials.status`)
```
ASSIGNED ─(학생 complete 업로드)─▶ COMPLETED
            ▲
            └─(재업로드)── 같은 row 유지, learning_material_answers만 대치
```

**API 요약** (자세한 계약은 `docs/API.md` §12 참고)
```
POST   /api/conversations/{id}/learning-materials           부여 (multipart: file/title/description)
GET    /api/learning-materials                              내 학습자료 (?targetStudentId 임퍼소네이트)
GET    /api/conversations/{id}/learning-materials           대화별 부여 목록
GET    /api/learning-materials/{id}/download                문제 PDF 다운로드 (attachment)
GET    /api/learning-materials/{id}/view                    문제 PDF inline 미리보기
POST   /api/learning-materials/{id}/complete                풀이 결과 업로드 (multipart files[])
GET    /api/learning-materials/{id}/answers/{aid}/file      풀이 파일 미리보기 (inline)
PATCH  /api/learning-materials/{id}/reviewed                강사/Admin 확인 토글
POST   /api/learning-materials/{id}/feedback                피드백 PDF 업로드 (multipart files[], PDF만) ← PR2
GET    /api/learning-materials/{id}/feedback/{fid}/file     피드백 PDF inline 미리보기 ← PR2
DELETE /api/learning-materials/{id}/feedback/{fid}          피드백 1건 삭제 ← PR2
DELETE /api/learning-materials/{id}                         영구 삭제 (cascade: answers + feedbacks + problem source)
```

**테스트 강사 계정** (`db/dev-seed/V901`)
- `login_id = tutor` / `password = admin1234` (V900 student와 동일 BCrypt 해시)
- 시드 학생(`login_id=student`)과 자동 매핑되어 conversation/자료 접근 가능

### 5.8 ✅ 랜딩 고정 영상 관리 — `/admin/landing`, `/`

관리 대상은 임의 영상 목록이 아니라 의미와 표시 순서가 고정된 3슬롯이다.

1. `HERO_BACKGROUND` — 히어로 배경
2. `ACADEMY_SHORTS_DEMO` — 학원 홍보 쇼츠
3. `PARENT_REPORT_DEMO` — 학부모 리포트

**권한/화면**

- `/admin/landing`과 `/api/admin/landing/videos/**`는 `ROLE_ADMIN`만 접근한다.
- 관리자 화면은 각 슬롯의 상태, 미리보기, 권장 비율, 파일 메타데이터, 교체 버튼, 공개 토글을 제공한다.
- 삭제 버튼과 `DELETE` API는 없다. 운영은 **교체·숨김·복원**만 지원한다.
- 공개 `/`와 visible managed stream의 GET은 비로그인도 접근할 수 있다.

**SSR 상태 해석**

| DB 상태 | `/` 렌더 |
|---|---|
| row 없음 | 방어적으로 기존 번들 mp4 |
| `visible=true`, managed asset 없음 | 기존 번들 mp4 |
| `visible=true`, managed asset 있음 | `/api/landing/videos/{slot}/stream` |
| `visible=false` | asset 유무와 무관하게 `<video>/<source>` 미렌더, 레이아웃용 placeholder/overlay 유지 |

`LandingPageController`가 한 번의 SSR 요청에서 세 상태를 해석해
`heroVideo`, `academyShortsVideo`, `parentReportVideo` 모델을 `public/landing`에 주입한다.
row 없음만 번들 fallback이며 실제 DB 장애는 숨기지 않는다.

**영상 교체**

```text
ADMIN 파일 선택
  → PUT multipart(video)
  → 서버 원본 크기/ffprobe 길이·codec 검증
  → ffmpeg H.264/AAC MP4 정규화 + poster 생성
  → 정규화 MP4 재-probe + 실제 길이 hard gate/메타데이터 확정
  → 새 mp4/thumbnail FileStorage 저장
  → 해당 슬롯 PESSIMISTIC_WRITE lock + REQUIRES_NEW DB asset 원자 교환
  → commit 성공
  → 이전 mp4/thumbnail best-effort 정리
  → LandingVideoAdminResponse 반환
```

- 제한 기본값: 업로드 100MiB 초과 거부, 길이 `N/A`/non-finite/0초 이하/60초 이상 거부,
  정규화 MP4 실제 길이도 같은 규칙으로 재검증, 변환 mp4 30MiB 초과 거부, thumbnail 2MiB 초과 거부.
- 변환/저장/DB 교환이 실패하면 새 파일만 보상 삭제하고 기존 row/공개본을 유지한다.
- 교체 transaction은 최신 `visible` 값을 보존하므로 숨김 슬롯을 교체해도 자동 공개하지 않는다.
- 교체 뒤 URL은 고정이고 응답은 `Cache-Control: no-store`라 다음 `/` 요청부터 빌드 없이 새 영상이 보인다.

**숨김/복원**

```text
PATCH {"visible": false}
  → asset은 보존
  → 다음 / SSR에서 media element 미렌더
  → public managed stream 직접 접근도 404

PATCH {"visible": true}
  → managed asset이 있으면 기존 업로드 영상 복원
  → asset이 없으면 번들 mp4 복원
```

**전송/실패 경계**

- public/admin managed 영상과 ADMIN thumbnail은 `FileStorage.readSingleLimited`만 사용한다.
- 영상은 저장된 `size_bytes`와 실제 바이트가 같아야 한다. 초과·불일치·저장소 IO 실패는 `503`이며 key/절대경로를 응답·로그에 노출하지 않는다.
- 교체 cleanup과 읽기가 경합해 기존 key 읽기가 실패하면 최신 row의 key가 바뀐 경우에만 한 번 재시도한다. public은 최신 visible도 다시 검사한다.
- 기본 동시 전송 permit은 3개다. permit은 전체 바이트 읽기부터 response body 쓰기 완료까지 유지하며 추가 요청은 `503`.
- 1차 구현은 full-buffer `200` 응답이다. HTTP Range(`206`, `Content-Range`), progressive streaming/CDN, 변경 이력, orphan reconciliation은 후속 범위다.

**API 요약** (상세 계약은 `docs/API.md` §16)

```text
GET    /api/admin/landing/videos                    고정 3슬롯 목록
PUT    /api/admin/landing/videos/{slot}             영상 교체 (multipart: video)
PATCH  /api/admin/landing/videos/{slot}/visibility  공개/숨김 ({visible:boolean})
GET    /api/admin/landing/videos/{slot}/stream      ADMIN managed mp4 미리보기
GET    /api/admin/landing/videos/{slot}/thumbnail   ADMIN managed poster 미리보기
GET    /api/landing/videos/{slot}/stream            visible managed mp4 공개 전송
```

**V932 → V933 병합/배포 gate**

`landing_video_slots`는 V933이다. 선점된
`V932__relax_lesson_product_required_columns.sql`이 아래 동일 blob/checksum으로 `origin/main`에 먼저 병합돼야 한다.

```bash
git fetch origin
git cat-file -e origin/main:src/main/resources/db/migration/V932__relax_lesson_product_required_columns.sql
test "$(git rev-parse origin/main:src/main/resources/db/migration/V932__relax_lesson_product_required_columns.sql)" = "3c34eea5654ba3b9233ab4f85a317c9ee6bff8ec"
test "$(git show origin/main:src/main/resources/db/migration/V932__relax_lesson_product_required_columns.sql | shasum -a 256 | awk '{print $1}')" = "a2a699e872a802041b1881a996e08a216a90526576cec7b2b1f5179be0a27a5e"
```

하나라도 실패하면 V933 PR 병합·운영 배포를 중단한다. 공유 DB에 적용된 V932는 재번호하거나 수정하지 않는다.

---

> 기존 PRD의 이메일 / 숙제 제작 / 숙제 풀이 / Admin 대시보드 — **후속 단계로 미룸**. 진단은 §7 후속 단계에서 별도 다룸.

---


## 12. Lesson Community / Q&A MVP

Lesson community는 자유 커뮤니티가 아니라 lesson 운영과 Q&A 처리를 위한 수업 단위 공간이다.

- 공간: `lesson_products.lesson_id` 단위.
- 참여자: lesson 소유 TUTOR, 해당 lesson APPROVED STUDENT, ADMIN.
- 글 유형: `GENERAL`, `QNA`, `NOTICE`.
- Q&A 기본값은 비공개이며, 작성 학생이 `qnaPublic=true`로 수업 참여자 공개 전환 가능.
- 공지와 커스텀 탭 생성/수정/삭제는 lesson 소유 TUTOR/ADMIN만 가능하다. 기본 탭은 `전체글`, `일반글`, `Q&A`, `공지`이며 최신 공지 1건은 공지 탭을 제외한 모든 탭 상단에 고정된다. 커스텀 탭 삭제는 탭 하드 삭제 후 기존 글을 일반글로 이동한다.
- 글/댓글 수정은 작성자만 가능하다.
- 글/댓글 삭제는 물리 삭제가 아니라 `is_visible=false` 숨김 처리이며 작성자/lesson 소유 TUTOR/ADMIN이 가능하다.
- 첨부는 community 전용 첨부 테이블에 저장하며 conversation source/homework로 자동 편입하지 않는다. 숨김 댓글의 첨부는 직접 URL 접근도 차단한다.
- lesson 삭제 시 community 데이터는 보존하는 방향이며, 실제 물리 삭제 정책과 충돌하는 경우 삭제 차단 후 Admin 관리 페이지에서 후속 처리한다.
- Admin은 `/admin/lesson-communities`에서 lesson community 단위로 검색하고, 결과에서 해당 커뮤니티 상세로 이동해 운영 상태를 확인한다.


### Academy Builder 히어로 영상 편집 메모

- `/builder` 히어로/원장 영상 박스 클릭 → 학원 영상 보관함의 업로드 영상 또는 YouTube 링크 선택. 업로드 영상 ID는 `pageSettings.builderDraft.heroVideoId`/`principalVideoId`, YouTube는 기존 `heroYoutubeUrl`/`principalYoutubeUrl`에 저장하며 슬롯별로 한 소스만 활성화한다. 기존 Academy는 상단 발행의 Full `PUT /api/tutor/academy`, 신규 발행 전 Academy는 pending `POST /api/tutor/academy` payload에 포함한다. 공개/빌더 렌더는 업로드 ID를 공개 영상 목록의 stream URL로 해석해 `<video>`로, YouTube URL은 embed iframe으로 표시한다.
- `/builder`의 `sec-videos` 영상 관리 버튼은 발행된 Academy에서만 `/tutor/academy/videos`로 이동한다. 발행 전 client-only Academy에서는 `학원 홈페이지를 먼저 발행한 후 영상을 등록할 수 있습니다.` 안내를 표시하고 이동을 막는다.

### Academy 영상 서버 multipart 업로드 → 동기 정규화 → 공개

```text
TUTOR click
  → POST /api/tutor/academy/videos multipart(video)
  → Spring multipart가 원본을 디스크에 spool
  → 서버 ffprobe로 120초/코덱을 재검증
  → 요청 스레드에서 H.264/AAC MP4 + poster 생성
  → private S3에 정규화 영상/thumbnail 저장
  → READY + NORMALIZED video row 저장
  → UI 목록 새로고침 및 즉시 공개
```

- 브라우저는 S3 presigned URL을 받거나 S3에 직접 PUT하지 않는다. 업로드 CORS가 사용자 경로의
  선행 조건이 되지 않도록 same-origin application endpoint로 복원했다.
- 원본/변환 산출물은 각각 200MiB 이하, 길이는 120초 이하, 학원당 최대 10개다.
- 변환과 S3 저장은 DB 트랜잭션 밖에서 수행하고 마지막 READY row insert만 저장한다. DB 저장 실패 시
  이미 저장한 영상/thumbnail을 보상 삭제한다.
- 공개 stable GET/HEAD는 published Academy + READY만 허용한다. local은 bounded Range stream,
  production은 30초 이하 no-store signed S3 redirect다.
- 삭제는 DELETING tombstone + cleanup outbox이며, 수업상품이 참조 중인 video는 먼저 참조를 해제한다.

운영·배포 gate는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md), HTTP 상세 계약은 [API.md](API.md)를 따른다.
- `/builder` 브라우저 URL 영역 클릭 → 공개주소(handle) 입력 모달 → `중복확인` 버튼 및 적용 시 `GET /api/tutor/academy/handle-available`로 unique 여부 확인 → 기존 `POST/PUT /api/tutor/academy`의 `handle` 필드로 저장한다.
- `/builder` 외부 연동 섹션 → 네이버 블로그/네이버 지도 URL 입력 모달 → `pageSettings.blogUrl`/`pageSettings.naverMapUrl` 저장. 기존 Academy는 `PUT /api/tutor/academy/page-settings`, 신규 발행 전 Academy는 pending payload에 포함한다. 저장된 URL은 Builder preview와 공개 페이지에서 버튼만 노출하지 않고 블로그/지도 썸네일형 카드로 렌더한다. Builder preview의 카드/썸네일/하단 버튼 클릭은 실제 외부 이동이 아니라 URL 수정 모달을 연다.
- `/builder` 레이아웃 탭 → ON section list(hero 포함) → hero LOCK/index 0, 나머지는 up/down 버튼만 제공한다. 공개/Builder 렌더는 `pageSettings.sectionOrder` 순서로 섹션 stack을 만들고, null/missing legacy rows는 canonical default full order로 fallback한다. `sectionVisibility`는 렌더/레이아웃 탭 표시 여부만 결정하며 full order slot은 유지한다.


### Academy Builder 수업대상/지역 편집 메모

- `/builder` preview 안의 수업대상/과목 뱃지 row를 클릭하면 모달을 열어 기존 Academy 기본 데이터를 수정한다. 지역 텍스트는 히어로에서 표시하지 않는다. 기존 Academy는 `PUT /api/tutor/academy` JSON 저장을 재사용하고, 신규 발행 전 Academy는 sessionStorage pending payload의 `subjectIds`/`targetGroups`/`regions`에 반영해 최초 발행 요청에 포함한다. 상단 `미리보기`, 실행취소, 다시실행, 일반 저장은 실제 기능이 아니므로 노출하지 않는다. desktop/tablet/mobile canvas 폭 전환은 실제 동작으로 유지한다.

### Academy Builder 텍스트 편집 메모

- `/builder` preview 텍스트는 `contenteditable="false"`로 렌더한 뒤, 사용자가 섹션 또는 field를 클릭하면 해당 섹션만 편집 가능하게 전환한다. 섹션 우상단 컨트롤(`.block-controls.builder-section-controls`)은 `적용`/`삭제` 버튼만 제공한다. 우측 스타일 컨트롤러는 노출하지 않으며, `적용`은 섹션 내 text-only target만 저장 흐름에 반영하며, `삭제`는 좌측 섹션 토글과 동일한 sectionVisibility OFF 동작을 수행한다(hero 제외). blur/Enter-to-blur 저장은 사용하지 않는다.
- 단일행 field는 Enter를 막고, multiline field는 `intro`, `introBodySecondary`만 줄바꿈을 허용한다(`introMotto`는 sec-intro 재구성으로 렌더에서 빠졌다. → [academy-builder-page.md](academy-builder-page.md) §9). 줄바꿈 표시 class는 Builder `builder-multiline-text`, 공개 페이지 `academy-multiline-text`를 사용한다.
- 기존 Academy는 섹션 적용 후 상단 `발행` 버튼으로 기존 `PUT /api/tutor/academy` 저장을 수행하고, 신규 Academy는 pending/sessionStorage에 누적했다가 최초 `POST /api/tutor/academy`에 포함한다. endpoint/request/response 계약 변경은 없다.

## 13. BUILD IT CHART — Google Educator Discovery V2

1차 범위는 BuildIt 홈페이지 연결, `/admin/chart`, 압구정·신사 V2 실제 검색, 승인/제외, 공개 Chart 반영까지다.

```text
BuildIt 공개 nav
  → /chart
  → GET /api/chart/educators
  → chart_educators(승인 row)만 TOP 100으로 렌더

ADMIN 로그인
  → /admin/chart
  → 검색 설정 조회/수정/기본값 복원
  → POST /api/admin/chart/discovery/run
  → Gemini + Google Search Grounding 또는 ChatGPT(OpenAI) + web_search (server only, timeout 120s)
  → CONTENT 검색 → 작성자/출연자 발견 → 후보 병합 → 후보별 Deep Search
  → chart_candidates DISCOVERED + VERIFIED/REVIEW_REQUIRED 저장
  → 관리자 [원문 확인] 후 [교육자로 승인] 또는 [제외]
  → 승인 시 chart_educators upsert
  → 공개 /chart 반영
```

### 범위 제한

- 현재 실제 실행 허용 권역은 `apgujeong-sinsa`(압구정·신사)뿐이다.
- 다른 생활권은 zone 목록에는 보이지만 확장 전 상태이며, 검색 실행은 차단한다.
- 대형학원·프랜차이즈·인강 중심 강사는 프롬프트와 서버 분류에서 제외/검토 대상으로 처리한다. 애매하면 `REVIEW_REQUIRED`로 남겨 관리자가 원문을 확인한다.
- AI가 자동으로 TOP 100에 등록하는 경로는 없다.

### 운영 보안

- `GEMINI_API_KEY` / `GEMINI_MODEL` 또는 `OPENAI_API_KEY` / `OPENAI_MODEL`은 서버 환경변수로만 사용한다.
- `/admin/chart`의 임시 LLM 키 입력은 서버 메모리에만 보관하고 DB/Git/프론트 저장소에 남기지 않는다.
- 복구본의 `.env`나 테스트 키는 repo에 추가하지 않는다.
- DB는 Flyway V947로 운영 스키마에 추가되며 재배포 시 초기화하지 않는다.
# BuildIt Wiki 단계형 작업 흐름

```text
Wiki 홈
  → 만들기 / 학생 / 지능 카테고리 선택
  → 카테고리별 2~5개 핵심 행동 선택
  → 선택한 전문 화면만 표시
  → 최소 내비게이션의 ← 카테고리 또는 BuildIt Wiki로 복귀
```

- `지능 → 수업 인사이트 기록`은 자유 텍스트와 선택 맥락을 `/problem-generator/wiki/api/educator-insights`로 저장하고 최근 5개를 다시 조회한다. 학교는 `/problem-generator/wiki/api/schools`의 기존 학교 DB를 자동완성으로 재사용한다.
- 문제 생성기의 학교 계층은 active Digital Twin뿐 아니라 기존 문제·학생 기록을 합쳐 표시한다. Twin이 연결된 시험만 해당 `twinId`로 생성 요청을 보내며, 기록만 있는 항목은 Twin 미등록 상태를 명시한다.
- 학교·학년을 선택하면 첫 시험과 해당 Digital Twin 버전을 자동 연결하고, Twin의 유형명을 문제 유형 카탈로그 ID로 역매핑해 기본 선택한다. 생성 요청에는 ID 리터럴이 아니라 유형 한글명과 세부 출제 지침을 전달한다.
- 생성된 객관식은 저장 전에 서버 품질 게이트를 거친다. 일반 객관식/순서형은 선택지 5개, 밑줄 번호형은 정확히 5개의 1~6단어 밑줄, 순서형은 줄 단위 `(A)·(B)·(C)` 문단을 강제한다. 실패하면 오류 내용을 포함해 한 번 자동 재생성하고, 재실패 결과는 DB에 저장하지 않는다.
- Assistant 첨부는 새 파일 저장소를 만들지 않는다. 이미지/PDF·학교 자료는 기존 `SchoolDatabase` OCR 흐름, 학생/오답 요청은 `StudentWrongNoteDashboard` OCR 흐름, HTML/JSON/TXT는 `DigitalTwinImport` 흐름으로 안내한다.
- 모든 Wiki API는 `/problem-generator/wiki/api/** → Spring Proxy → FastAPI`를 유지한다.
