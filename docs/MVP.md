# 과외 솔루션 MVP 명세 (v2)

> 서비스 담당자 요구사항 재정의 기반. **학생(Student) 중심 구조**로 전환.
> 강사(Tutor) 역할 제거 → 원장이 Admin으로 대체.
> 핵심 가치: "사람이 할 수 없는 것"을 AI로 자동화 — 학생이 올린 자료 기반 영어 수준 자동 진단.

---

## 0. 장기 비전 (플랫폼 방향성)

이 서비스는 궁극적으로 **학생-강사 매칭 플랫폼**(배달의민족 스타일)을 지향한다.
학생이 자기 학습 자료를 업로드 → LLM이 해당 자료를 분석 → 적합한 **강사(TUTOR)**와 자동 매칭되는 구조다.

### 단계별 진행

| 단계 | 범위 | 상태 |
|------|------|------|
| **1차** | 학생 자료 **업로드 + LLM 분석 + 저장** | 🚧 **현재 작업 단계** |
| 2차 | 진단 / 숙제 / 이메일 / Admin 대시보드 강화 등 학습 보조 기능 | 후속 |
| 3차 | TUTOR 역할 활성화 + 학생-강사 자동 매칭 시스템 | 후속 (장기) |

> **TUTOR enum은 미리 예약**해 둔다 (1차에서는 사용하지 않음). 실제 매칭 플로우·UI는 3차 단계에서 설계한다.
> ADMIN은 1차에서는 시드 계정만 유지하며 별도 플로우 없음.

---

## 1. MVP 범위 (구현 대상)

| 기능 | 포함 여부 |
|------|-----------|
| 회원 가입 / 로그인 / 개인정보 수정 / 회원탈퇴 (학생, Admin) | ✅ |
| 학생 — 학교 자료 업로드 (PDF / 이미지 / 모든 형식) | ✅ |
| LLM 대화 — 업로드 파일 기반 세션 단위 채팅 | ✅ |
| 영어 수준 진단 — LLM이 자료 읽고 문제 자동 생성 | 🔜 후속 단계 |
| 진단 테스트 서비스 내 풀이 (구글폼 스타일) + 자동 채점 | 🔜 후속 단계 |
| 진단 결과 간략 리포트 + 다음 방문일 안내 | 🔜 후속 단계 |
| 이메일 알림 (다음 방문일 등) | 🔜 후속 단계 |
| 숙제 — Admin이 문제+해설 업로드 → 학생에게 배포 + 자동 채점 | 🔜 후속 단계 |
| 학생 대시보드 (자료 목록, 대화 이력, 검증 대기 시험지) | ✅ |
| Admin 대시보드 (학생 목록, 시험 자료 보기) | ✅ |
| Admin → 학생 임퍼소네이트 (학생 본인과 동일 권한으로 작업) | ✅ |
| 자료 영구 삭제 (학생 또는 ADMIN, cascade) | ✅ |
| 강사-학생 과목 매핑 (TutorStudentMap) | ❌ 제거 |
| 시험지 OCR / 강사 검수 / 성적 입력 | ❌ 제거 |
| 학부모 계정 / 로그인 | ❌ MVP 제외 (학부모 연락처는 students 테이블에 직접 저장) |
| 카톡 / 문자 알림 | ❌ MVP 제외 |
| 유사 문제 자동 생성 | ❌ MVP 제외 |
| 인증/인가 (JWT cookie + IDOR 가드) | ✅ |

> **🎯 1차 작업 우선순위 (현재 단계)**
>
> 위 ✅ 항목 전부가 MVP 최종 형태이지만, **1차 작업 단계에서는 다음만 구현**한다:
> - 학생 / Admin 회원 가입 / 로그인 — JWT(HS256) HTTP-only Cookie 기반. 모든 보호 API는 쿠키에서 principal을 꺼내 본인 자원만 접근하도록 service-layer ownership 가드로 닫힘 (§5.1 인증 정책 참고).
> - 학생 자료 업로드 (시험지·일반 자료 혼합 가능, 파일 저장 + `sources` INSERT). **contentKind는 학생이 폼에서 명시** (`GENERAL` / `EXAM_BLANK`) — LLM 분류 호출 없음.
> - **자동 분석 (시험지에만)**:
>   - **`GENERAL`** → 파일·DB 저장만 즉시 COMPLETED 처리 후 학생에게 안내 (LLM 0회)
>   - **시험지** → 메타 합성 + `exams` dedup → LLM 1회로 `questions` / 학생 답안 추출 → DB 저장. 채점 흔적이 있으면 자동으로 `EXAM_GRADED`로 promote.
> - 시험지(`EXAM_GRADED`)인 경우: **메타 + 채점 검증 UI 한 페이지**에서 학생이 확정 → 메타 update + `student_answers` / `student_scores` 저장.
> - 시험지(`EXAM_BLANK`)는 검증 UI 없이 questions만 INSERT 후 즉시 COMPLETED.
> - 대화 저장 시 `metadata` 컬럼 활용 (학생정보·과목·연결 source 요약·시험 점수 등).
>
> 대화(`conversations` 채팅 응답) / 진단 / 숙제 / 이메일 / Admin 대시보드는 **2차 작업** 이후로 미룬다.
> 자세한 1차 플로우는 **§5.0** 참고.

---

## 2. 기술 스택

| 항목 | 내용 |
|------|------|
| 백엔드 | Spring Boot |
| 프론트엔드 | Thymeleaf (SSR) — HTML은 `src/main/resources/templates`, CSS/JS/이미지는 `src/main/resources/static` |
| DB | MySQL (Docker로 빈 DB 기동 → Spring Boot 부팅 시 **Flyway**가 `src/main/resources/db/migration` 마이그레이션 자동 적용. 로컬은 `db/dev-seed`에서 시드 계정·더미 데이터 추가 적용) |
| 파일 저장 | 로컬 파일시스템 (운영은 S3 — `app.file.storage-type=s3`) |
| 인증 | **JWT(HS256) + HTTP-only Cookie**. `JwtAuthenticationFilter`가 쿠키에서 토큰을 파싱해 `SecurityContext`를 채우고, `AuthenticatedUserResolver`가 컨트롤러/서비스 양쪽에서 IDOR 가드를 수행. 운영은 `JWT_SECRET` 환경변수로 비밀키 주입 (§5.1). |
| LLM | Claude API (`claude-sonnet-4-6`) — 대화 + 시험지 메타·문제·답안 추출 |
| Vision | Claude Vision API — 시험지 이미지/PDF 분석 |

---

## 3. 사용자 모델 변경

### 기존 → 신규 비교

| 기존 | 신규 | 변경 내용 |
|------|------|----------|
| TUTOR (강사) | **ADMIN (원장)** | 숙제 제작·배포, 학생 관리. 로그인하여 Admin 전용 화면 사용 |
| STUDENT | **STUDENT** | 기본(default) 사용자. 자료 업로드, 진단, 숙제 풀이 |
| PARENT | ❌ 제거 | 학부모 연락처는 `students.parent_name` / `parent_phone`으로 직접 저장 |

- **`tutor_student_map` Phase 2 부활 → V9 이후 권한 가드 SoT 이전** — 테이블은 존재하나 TUTOR 권한 판정은 `lesson_applications.status=APPROVED` 기반으로 교체됨 (cleanup PR에서 DROP 예정). `AuthenticatedUserResolver`가 이 단일 쿼리로 conversation·자료 접근을 제어한다.
- 학생 회원가입 시 학교명, 학년, 학부모 정보를 함께 수집한다
- 학생/강사는 `/my/profile`에서 개인정보를 수정하고 현재 비밀번호 확인 후 회원탈퇴할 수 있다. ADMIN은 운영 계정 보호를 위해 해당 self-service 탈퇴/수정 플로우에서 제외한다.

### 개인정보 수정/회원탈퇴 플로우

- `GET /my/profile`은 학생/강사용 self-service 화면이다. ADMIN 접근은 `/home`으로 redirect한다.
- `GET /api/me`는 JWT principal의 `userId` 기준으로 본인 프로필만 반환한다.
- `PATCH /api/me`는 `name`, `email`, `phone`을 선택 수정하고, STUDENT는 `schoolName`, `grade`, `parentName`, `parentPhone`도 선택 수정한다. `loginId`와 `role`은 요청으로 받지 않는다.
- 비밀번호 변경은 `newPassword`가 있을 때만 수행하며 `currentPassword` 검증을 통과해야 한다.
- `DELETE /api/me`는 `currentPassword` 검증 후 `users` row 삭제를 루트로 탈퇴를 수행한다. 학생 소유 데이터는 FK cascade로 삭제되고, 강사가 배포자로만 연결된 학습자료는 `learning_materials.assigned_by`가 `NULL`로 남는다.

### ✅ TUTOR 역할 활성화 — Academy/Lesson 판매 페이지

`Role.TUTOR`는 더 이상 예약값만이 아니다. 강사 회원가입 후 본인 소유의 공개 Academy를 만들고, 그 Academy에 수업 상품(Lesson)을 등록할 수 있다.

- 학생 영역(`/register`, `/stdt_dashboard`, conversation/upload/chat)은 그대로 유지한다.
- 강사 영역은 `/tutor/*` SSR + `/api/tutor/**` API로 분리한다.
- 공개 Academy 조회는 비로그인 허용, 생성/수정은 `ROLE_TUTOR` + 소유자 검증을 모두 통과해야 한다.
- 학생 수업 검색/자동 신청은 §5.6에서 구현 완료됐다. 결제/구독은 아직 후속 단계다.

---


## 4. 도메인 모델 → [DOMAIN.md](DOMAIN.md)
테이블 24종 스키마(users, conversations, academies, lesson_products, learning_materials …)는 분리됨. **스키마 작업 시 [DOMAIN.md](DOMAIN.md) 로드.**

## 5. 기능별 플로우 → [FLOWS.md](FLOWS.md)
자료 업로드·LLM 분석, 인증/인가, 대시보드, 오답노트, Academy/Lesson, 학습자료 부여 등 플로우는 분리됨. **플로우 작업 시 [FLOWS.md](FLOWS.md) 로드.**

## 6. 패키지 구조 (Spring Boot)

```
src/main/java/f2r/tutor_solution/private_tutor_solution_back/
├── config/
│   ├── SecurityConfig.java                   filter chain + path allowlist + JwtAuthenticationFilter 등록
│   ├── GlobalExceptionHandler.java           IllegalArgumentException→400, AccessDeniedException→403
│   ├── AuditorContextHolder.java             BaseEntity created_by/updated_by 자동 채움 (JWT 필터에서 set)
│   └── security/                             ⭐ JWT 인증·인가 인프라
│       ├── JwtProperties.java                @ConfigurationProperties("app.jwt")
│       ├── JwtTokenProvider.java             issue/parse/검증 (HS256)
│       ├── CookieUtil.java                   HttpOnly+SameSite=Lax 쿠키 헬퍼
│       ├── JwtAuthenticationFilter.java      쿠키 → SecurityContext + AuditorContextHolder
│       └── AuthenticatedUserResolver.java    IDOR 가드 (컨트롤러 resolveUserId/StudentId + 서비스 verifyOwnsConversation/Source)
└── domain/
    ├── user/                  (User, Student, AuthController, UserController)
    ├── subject/               (Subject, QuestionCategory, Passage)
    ├── exam/                  (Exam, ExamType, ExamRepository)
    ├── source/                (Source — 파일 업로드/저장 관리)
    │   ├── service/
    │   │   ├── SourceService.java                업로드 + 비동기 분석 트리거 + cascade 삭제(delete)
    │   │   ├── SourceAnalysisService.java        묶음 분석 흐름 (메타 합성 → exams dedup → questions INSERT → 채점 흔적 판별)
    │   │   ├── ExamMetaInferenceService.java     LLM 메타 유추 (LLM 1회)
    │   │   ├── ExamExtractionService.java        LLM 문제+답안 추출 (LLM 1회)
    │   │   └── ExamReviewService.java            검증 페이지 + 학생 확정 (verifyOwnsSource)
    │   └── controller/
    ├── conversation/          (Conversation, ConversationSource, ConversationMessage)
    │   ├── service/
    │   │   ├── ConversationService.java          대화 CRUD + 첨부 파일 관리 (verifyOwnsConversation)
    │   │   └── ClaudeChatService.java            Claude API 호출 (파일 컨텍스트 + Prompt Caching)
    │   └── controller/
    ├── dashboard/             (radar/timeline — WeaknessAnalysisService도 verifyOwnsConversation 호출)
    ├── academy/               (Academy, LessonProduct — TUTOR 공개 Academy/수업 상품)
    ├── learningmaterial/      (LearningMaterial — Admin/TUTOR PDF 학습자료 부여/풀이 업로드)
    ├── lessonapplication/    (LessonApplication — 학생 수업 검색/신청/취소)
    ├── question/              (Question, StudentAnswer)
    ├── score/                 (StudentScore)
    └── admin/                 (Admin 화면 컨트롤러 — 학생 목록 + ADMIN role 가드)
└── infrastructure/
    ├── storage/                (FileStorage — 파일 저장/조회)
    └── image/                  (ImageSanitizer — 업로드 이미지 JPEG 재인코딩/EXIF 보정)
```

> `TutorStudentMap` entity/repository는 `domain/user/` 안에 둔다. 후속 단계에서 도입 예정 패키지: `diagnosis/` (영어 수준 진단), `homework/` (자동 채점 숙제), `notification/` (이메일 알림).

---

## 7. MVP 이후 확장 계획

- **🌐 학생-강사 매칭 플랫폼화 (§0 3차 단계)** — 현재 TUTOR Academy/Lesson은 구현됨. 후속으로 학생 검색/수업 신청/결제 + 학생 자료 분석 결과(`metadata`, `student_scores`, `student_answers`) 기반 자동 매칭. 배달의민족 스타일.
- **docx 학습자료 자동 출제 (§5.7 후속)** — 강사가 docx 파일을 업로드하면 LLM이 문제·해설을 자동 구조화해 학생에게 인앱 풀이 UI로 제공. 현재는 PDF만 부여 + 학생이 종이로 풀이.
- ✅ **강사 채점·피드백 PDF (§5.7 PR2 구현 완료)** — 학생 풀이 결과를 받은 후 강사가 PDF로 피드백을 첨부해 다시 학생에게 돌려주는 흐름. V11 `learning_material_feedbacks` 테이블(§4.24) + `source_type=HOMEWORK_FEEDBACK`(§4.5) + JPA 엔티티 + 피드백 업로드/미리보기/삭제 엔드포인트 모두 구현 완료.
- 카카오톡 알림톡 / 문자(SMS) 알림 추가
- 학부모 계정 (PARENT 역할) + 자녀 학습 현황 열람
- 진단 결과 기반 맞춤 숙제 자동 추천
- Admin 화면에서 LLM 추출 문제 직접 편집
- 과목 확장 (수학, 국어 등)
- **ADMIN 임퍼소네이트 흐름 분리** — 현재는 `?targetStudentId=` query로 통과시키지만, 학생 검색 + 별도 ADMIN-only 엔드포인트로 깔끔하게 분리 예정 (§5.1)
- **JWT refresh token** — 현재는 access token 단독(24h). refresh token으로 보안/UX 양쪽 보강
- React SPA 전환
- **Claude Agent (Tool Use) 도입** — 진단 문제 생성을 단일 호출이 아닌 다단계 에이전트로 전환 (카테고리 자동 분류, 자료별 난이도 조절 등 복잡한 판단이 필요해질 때)


## 부록. UI/템플릿 컨벤션 → [CONVENTIONS.md](CONVENTIONS.md)
Thymeleaf head fragment, admin topnav, 학교 시험분석, Academy Blog/SEO 컨벤션은 분리됨.
