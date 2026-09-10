# 도메인 모델 (Domain Model)

> 과외 솔루션 DB 테이블 스키마. 마이그레이션 파일(`db/migration`)·Entity와 항상 동일해야 한다.
> 상위 개요: [MVP.md](MVP.md) · 플로우: [FLOWS.md](FLOWS.md)

## 4. 도메인 모델

### Wiki SQLite: 고3 수능 영어 저장소

Wiki FastAPI의 기존 SQLite 데이터에 additive 방식으로 `csat_question_sources`, `csat_questions`, `csat_wrong_answers`를 추가한다. Spring/JPA 운영 스키마와 별도인 Wiki 저장소이므로 Flyway 번호는 사용하지 않는다.

- `csat_question_sources`: `exam_kind`, 원본 파일명, SHA-256 `source_hash`, 시험 메타데이터 JSON, parser, 추출 원문, 원본 저장 여부(현재 항상 false), owner scope(현재 `shared`), 생성 시각을 보관한다. `(source_hash, exam_kind)`로 중복 업로드를 막는다. 바이너리 원본 자체는 저장하지 않는다.
- `csat_questions`: source FK, half/full, 18~45 번호, 표준 유형 id/name, passage, prompt, choices JSON, answer/solution, footnote, group, source reference, 유형 불일치 flag/reason, raw JSON을 보관한다.
- `csat_wrong_answers`: 학생, 시험, 번호, 표준 유형, 오답, 정답, 메모, 원본 파일명을 보관한다. 기존 `student_wrong_notes`와 FK나 overwrite 관계가 없다.

### Wiki SQLite: Generated Question Store (MCP 생성문항)

기존 Wiki SQLite에 additive로 `source_materials`, `generated_tests`를 추가하고 `generated_questions`에 `test_id` 컬럼을 더한다. `Source Material → Generated Test → Questions → Answer/Explanation` 관계로, MCP `save_generated_test`가 유일한 write 경로다.

- `source_materials`: Wiki 전체에서 재사용하는 학습자료 registry. `school`, `grade`, `title`, `publisher`, `lesson`, `material_type`, 선택적 `school_print_material_id`(→ `exam_material_sets.id`, 추적용), 생성 시각. `(school, grade, title)` UNIQUE로 저장 시 upsert하여 같은 자료가 시험마다 중복 생성되지 않는다.
- `generated_tests`: 생성 시험 1건. `external_id`(idempotency, `<> ''`일 때 UNIQUE — 같은 요청 재시도 시 재삽입 대신 기존 test 반환), `source_material_id`(FK, 필수), `source_page`, 시험 Context(school/grade/year/semester/exam), `test_type`, `title`, `twin_id`(→ `digital_twin_profiles.id`, nullable) + `twin_label` + `twin_pattern`, `difficulty`, `target_vocabulary`/`target_grammar`(JSON 배열), `target_passage`, `generation_source`(기본 `'mcp'`), `question_count`, `raw_request_json`.
- `generated_questions.test_id`: nullable FK → `generated_tests.id`. 기존 신규·변형·`save_generated_questions` 저장 row는 NULL 유지. `save_generated_test` 저장분만 세팅되며 문항 자체는 기존 canonical 계약(`mode='mcp'`) 그대로다.
- 저장은 all-or-nothing: 문항 계약 검증을 어떤 쓰기보다 먼저 하고, 트랜잭션 도중 실패 시 롤백 후 생성 성공과 구분되는 오류를 낸다.

### 핵심 설계 원칙
1. **`questions`는 모든 문제의 단일 저장소다.**
   - 1차 단계에서는 시험지에서 LLM이 추출한 문제(`source=READFILE`)만 INSERT된다. 후속 단계에서 LLM 생성(`GENERATED`) / Admin 직접 입력(`MANUAL`) 흐름이 추가된다.
   - `questions`는 재사용 풀이 아닌 **출제 기록**이다. 시험지 단위로 새 row를 생성하며 시험 간 내용 중복은 허용한다.
   - `category_id`를 통해 카테고리별 정오답 집계(취약점 분석)가 가능하다.
2. **답안은 `student_answers` 하나로 통합한다.**
   - 1차에서는 시험지 답안(`exam_id` 채워짐)만 들어온다. 후속 단계의 진단/숙제 답안도 본 테이블에 합쳐 관리한다.
3. **대화 세션(`conversations`)은 첨부 파일이 스코프를 결정한다.**
   - 한 대화에 첨부된 파일들만 해당 세션의 LLM 컨텍스트로 사용된다.
   - 새 대화 = 새 파일 스코프. 기존 대화 재입장 = 히스토리 + 파일 이어서 사용.
4. **시험지는 `exams` 마스터로 정규화하되, dedup은 "best-effort"다.**
   - dedup 매칭 키: `(school_name, grade, exam_type, exam_year, exam_semester, subject_id)` — DB에는 **`INDEX idx_exams_dedup_key`** (UNIQUE 아님)
   - **중복 row를 허용**한다. 학생/LLM 메타가 다르게 추출되면 동일 시험이라도 별도 `exam_id` 생성 가능.
   - 학생의 questions access는 자기 `student_answers`/`student_scores`가 자기 `exam_id`를 가리키므로 항상 보장됨.
   - 같은 시험 중복은 추후 **배치 dedup**으로 정리(예: `exams.canonical_exam_id` self-FK로 soft-merge). 1차 미구현.
   - 학생의 raw 파일(`sources`)은 학생별로 보관 (dedup 안 함).

> **📌 BaseEntity 공통 컬럼 (모든 테이블에 자동 적용)**
>
> 아래 §4.1~§4.22 표에는 도메인 컬럼만 적었다. 실제 DB(Flyway 마이그레이션 `src/main/resources/db/migration/V1__init_schema.sql`) + Entity는 거의 모든 테이블에 다음 컬럼을 추가로 가진다:
> - `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
> - `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
> - `created_by VARCHAR(50)` — `AuditorAware`로 자동 세팅 (loginId)
> - `updated_by VARCHAR(50)`
>
> 예외: `conversation_sources`, `conversation_messages`, `student_answers` 등 일부 매핑/이력성 테이블은 일부만 보유. 정확한 컬럼 구성은 `V1__init_schema.sql` 참고.

---

### 4.1 users

| 컬럼 | 타입 | 설명 |
|------|------|------|
| user_id | BIGINT PK | |
| login_id | VARCHAR(50) UNIQUE | 로그인 ID |
| password | VARCHAR(255) | 암호화 비밀번호 |
| name | VARCHAR(30) | 이름 |
| nickname | VARCHAR(50) | 사용자 표시 닉네임. 필수, 중복 허용. 기존 사용자는 `login_id`로 backfill |
| email | VARCHAR(100) | 이메일 (알림 발송용) |
| phone | VARCHAR(20) | 전화번호 |
| role | ENUM | STUDENT / ADMIN / **TUTOR** |

### 4.2 password_reset_tokens

비밀번호 재설정 링크용 1회성 토큰. 원문 토큰은 저장하지 않고 SHA-256 해시만 저장한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| password_reset_token_id | BIGINT PK | |
| user_id | BIGINT FK | users.user_id, 사용자 삭제 시 cascade |
| token_hash | VARCHAR(64) UNIQUE | reset token SHA-256 hex digest |
| expires_at | TIMESTAMP | 생성 후 60분 만료 |
| used_at | TIMESTAMP NULL | 사용 완료 시각. NULL이면 미사용 |
| created_at / updated_at / created_by / updated_by | audit | 공통 audit 컬럼 |

### 4.3 students
`users`와 1:1. 학생 추가 정보 + 학부모 연락처.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| student_id | BIGINT PK | |
| user_id | BIGINT FK UNIQUE | users.user_id |
| school_name | VARCHAR(100) | 학교명 |
| grade | TINYINT | 학년 |
| parent_name | VARCHAR(30) | 학부모 이름 |
| parent_phone | VARCHAR(20) | 학부모 전화번호 |

### 4.4 subjects
과목 마스터. MVP는 영어만 사용.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| subject_id | INT PK | |
| name_ko | VARCHAR(50) UNIQUE | 한글명 |
| name_en | VARCHAR(50) UNIQUE | 영문명 |

### 4.4 question_categories
영어 카테고리 (취약점 분석 집계 기준).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| category_id | BIGINT PK | |
| subject_id | INT FK | |
| category_name_ko | VARCHAR(50) | |
| category_name_en | VARCHAR(50) | |

> **시드 데이터 (영어):** 어휘(Vocabulary) / 문법(Grammar) / 암기(Memorization) / 독해(Reading Comprehension) / 기타(Other)
> `other`는 듣기·작문·말하기 등 4종에 정확히 끼지 않는 영역의 폴백. LLM이 명확하게 분류하지 못하면 `other` 또는 null로 응답한다. lookup 키는 `category_name_en` (`vocabulary` / `grammar` / `memorization` / `reading` / `other`).

### 4.5 sources ⭐ 업로드 자료 원본 저장소
학생·Admin이 올리는 모든 파일. 형식 무관 (시험지, 교재, 필기, 문제지, 해설지 등).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| source_id | BIGINT PK | |
| uploaded_by | BIGINT FK | users.user_id |
| original_file_name | VARCHAR(255) | 원본 파일명 |
| storage_key | VARCHAR(500) | 로컬 저장 경로 (IMAGE: 단일 JPEG 경로 / PDF: 페이지 디렉터리) |
| file_type | VARCHAR(10) | PDF / IMAGE / UNKNOWN |
| file_size | BIGINT | byte |
| source_type | ENUM | STUDENT_MATERIAL(학생 업로드) / HOMEWORK_PROBLEM(강사 부여 숙제 문제) / HOMEWORK_ANSWER(학생 숙제 제출) / TUTOR_MATERIAL(강사 시험대비 참고자료, 제출 불필요) / **HOMEWORK_FEEDBACK(강사 피드백 PDF, V11 추가 — PR2 엔드포인트 구현 완료)** / WRONG_NOTE(오답노트 수동 업로드, V904) / **CONSULTATION(강사/Admin 상담 피드백 자료, V911)** |
| **content_kind** | ENUM | **GENERAL / EXAM_BLANK / EXAM_GRADED / UNCLASSIFIED** — 학생 폼은 GENERAL/EXAM_BLANK만 보낼 수 있다. EXAM_GRADED는 분석 단계에서 채점 흔적 감지 시 서버가 promote (§5.0 4단계). UNCLASSIFIED는 폼 비어 들어왔을 때의 폴백. |
| **processing_status** | ENUM | **PENDING / ANALYZING / PENDING_REVIEW / COMPLETED / FAILED** — 분석 워크플로 상태. 채팅은 첨부 source가 모두 COMPLETED일 때만 허용. PENDING_REVIEW는 EXAM_GRADED만 진입. |
| **analysis_result** | JSON | 시험지 분석 결과 (meta + questions[].student_answer/is_correct + matched_exam_id + total/correct). EXAM_GRADED일 때만 채워지고, 학생 확정 시 `student_answers`/`student_scores`로 풀어서 INSERT된다. GENERAL/EXAM_BLANK는 NULL. |
| exam_id | BIGINT FK | nullable — 시험지로 분류 + 메타 합성 후 연결되는 시험 마스터 (`exams.exam_id`) |
| uploaded_at | DATETIME | |

> **`TUTOR_MATERIAL`** (V10, `feature/conversation-sidebar-nav`): 강사/Admin이 학생에게 '제출 불필요 참고자료'(시험대비 자료)를 올릴 때의 source_type. 전용 엔드포인트 `POST /api/conversations/{id}/tutor-materials`로 업로드되며 ContentKind=GENERAL 고정. 기존 3종(STUDENT_MATERIAL=학생 업로드, HOMEWORK_PROBLEM/HOMEWORK_ANSWER=숙제 부여/제출)이 모두 제출·학생 소유 축이라 '강사가 주는 비숙제 자료'를 담을 값이 없어 추가됨.

> **`CONSULTATION`** (V911, `feature/consultation-feedback`): 강사/Admin이 conversation 대시보드 "개별 상담 및 피드백" 카드에 직접 올리는 상담 자료(파일만, 텍스트 코멘트 없음). 숙제(LearningMaterial)·시험 매칭 없이 `conversation_sources`로 conversation에 직접 매핑된다. 전용 엔드포인트 `POST/DELETE /api/conversations/{id}/consultation-materials`로 업로드/삭제하며 ContentKind=GENERAL 고정. 권한: 강사=본인 담당 conversation 업로드+삭제, 학생=조회+다운로드(삭제 불가), Admin=전부. Update 없음(삭제 후 재업로드). 물리삭제. 조회는 conversation detail의 `sources`를 `source_type=CONSULTATION`으로 필터, 썸네일/다운로드는 `GET /api/sources/{id}/file|download` 재활용(`verifyOwnsSource` conversation-scoped).

### 4.5.2 problem_generator_question_sets / problem_generator_set_questions ⭐ 생성 문제 저장소

`/problem-generator`에서 Gemini가 만든 문제를 사용자별로 저장하는 묶음/매핑 테이블. 긴 paragraph/지문과 개별 문제는 기존 `passages` / `questions`를 재사용한다.

- `problem_generator_question_sets`: owner, 연결 Digital Twin 자료, 생성 프롬프트, raw Gemini JSON, visibility를 저장한다.
- `problem_generator_set_questions`: 저장 묶음과 `questions(question_id)`를 순서대로 연결한다.
- 생성 문제는 `questions.source=GENERATED`로 저장한다.
- 생성 시 입력한 긴 지문/paragraph는 `passages`에 1건 저장하고 해당 묶음의 문항들이 같은 `passage_id`를 공유한다.
- 조회 범위는 Digital Twin 자료와 동일하게 내 PRIVATE 묶음 + PUBLIC 묶음이다.
- 수정(Update)은 LLM 결과 보정 목적상 1단계에 포함한다: 공유 지문, 발문, 선택지, 정답, 해설, 배점을 수정할 수 있다.
- 삭제(Delete)는 묶음 삭제 시 이 묶음에서 생성한 GENERATED `questions`와 공유 `passages`를 함께 정리하는 물리 삭제다.

### 4.5.1 problem_generator_materials ⭐ Problem Generator 자료 저장소

`/problem-generator` 1단계의 Digital Twin 자료 업로드 전용 테이블. 기존 `sources` / `conversation_sources`를 재사용하지 않고, 문제 생성 스튜디오 안에서 독립적으로 접근 제어한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| material_id | BIGINT PK | |
| uploaded_by | BIGINT FK | `users.user_id`, 사용자 삭제 시 CASCADE |
| title | VARCHAR(150) | 자료 제목. 다중 파일 업로드 시 `제목 · 원본파일명`으로 파생하되 150자 이내로 자른다 |
| original_file_name | VARCHAR(255) | 원본 파일명 |
| storage_key | VARCHAR(500) | `FileStorage` 저장 키. `UploadedFileIngestor`가 생성 |
| file_type | VARCHAR(10) | IMAGE / PDF / UNKNOWN 저장 파이프라인 값. 1단계 API는 Digital Twin 이미지 업로드만 허용 |
| file_size | BIGINT | 저장 후 크기 byte |
| visibility | ENUM | PRIVATE / PUBLIC. PRIVATE는 업로더 본인만, PUBLIC은 전체 로그인 사용자가 조회 가능 |
| created_at / updated_at / created_by / updated_by | audit | `BaseEntity` 공통 audit 컬럼 |

권한:
- STUDENT/TUTOR/ADMIN은 `/problem-generator/**` 화면에 접근 가능하다.
- `POST /api/problem-generator/materials`는 항상 PRIVATE 자료를 만든다.
- PUBLIC 등록 및 visibility 변경은 ADMIN 전용(`/api/admin/problem-generator/**`).
- Delete는 업로더 본인 또는 ADMIN만 가능하며, DB row 삭제 후 storage 객체를 best-effort 삭제한다.
- 1단계는 자료 업로드/조회, BYO Gemini 키 기반 문제 생성, 생성 문제의 사용자별 저장/조회/수정/삭제까지 실동작한다.

### 4.6 conversations ⭐ LLM 대화 세션 (신규)
학생 또는 강사의 Claude 대화 세션. 창(탭) 1개 = 대화 1개. 기존 대화는 이어서 사용 가능.

V925부터 `student_id`와 `tutor_user_id`는 둘 다 nullable이지만 **정확히 하나만** 채워야 한다(exact-one-owner). DB `CHECK chk_conversations_exact_one_owner`와 Entity 생성자에서 같은 규칙을 검증한다. Entity 생성은 `Conversation.forStudent(...)` / `Conversation.forTutor(...)`만 사용한다.

V925 migration: `student_id` nullable 변경, `tutor_user_id` 추가, `fk_conversations_tutor_user`(`users.user_id`, ON DELETE CASCADE), `chk_conversations_exact_one_owner`, `idx_conversations_tutor_user_recent(tutor_user_id,last_message_at,created_at)` 추가.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| conversation_id | BIGINT PK | |
| student_id | BIGINT FK NULL | 학생 소유 대화일 때만 값 존재 |
| tutor_user_id | BIGINT FK NULL | 강사 소유 기록일 때만 값 존재. V925 `fk_conversations_tutor_user` → `users.user_id`, `idx_conversations_tutor_user_recent` |
| title | VARCHAR(200) | 대화 제목 (첫 메시지 기반 자동 생성) |
| metadata | JSON | 학생 대화 메타데이터 — 학생정보 스냅샷, 과목, 연결된 source 요약, LLM 분석 결과 요약 등. 강사 소유 기록은 현재 NULL |
| created_at | DATETIME | |
| last_message_at | DATETIME | |

소유권 분리:
- student-owned: 기존 `/conversation/*`, `/api/conversations/**` 학생형 플로우. TUTOR는 담당 학생 read guard로 조회 가능하지만 생성/PATCH/DELETE 관리는 금지.
- tutor-owned: `/tutor/conversation_dashboard`, `/api/tutor/conversations/**` 강사 개인 기록. owning TUTOR만 접근 가능하며 학생/Admin/다른 강사는 403.

### 4.7 conversation_sources
대화 ↔ 첨부 파일 M:N 매핑. 이 테이블에 연결된 파일들이 해당 세션의 LLM 컨텍스트가 된다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| conversation_id | BIGINT FK | |
| source_id | BIGINT FK | |

### 4.8 conversation_messages
대화 메시지 히스토리. 기존 대화 재입장 시 전체를 Claude API에 전달한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| message_id | BIGINT PK | |
| conversation_id | BIGINT FK | |
| role | ENUM | USER / ASSISTANT |
| content | TEXT | |
| created_at | DATETIME | |

### 4.9 passages
독해 지문 마스터. 한 시험 안에서 여러 문제가 공유하는 본문을 정규화해 저장한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| passage_id | BIGINT PK | |
| subject_id | INT FK | |
| title | VARCHAR(255) | nullable — **§5.0 4단계에서 자동 채움**. content의 첫 줄 일부를 잘라 사용 (최대 60자, 너무 길면 `...`). LLM은 본문만 응답하면 됨. |
| content | LONGTEXT | 본문 (LLM이 추출) |
| source_origin | VARCHAR(255) | nullable — **§5.0 4단계에서 자동 채움**. 해당 지문이 속한 `exams.display_name` (예: "00중학교 1학년 2024 1학기 중간고사"). |

> **1차 단계에서 자동 채워짐.** 시험지 추출 시 LLM이 `passages` 마스터 배열을 별도로 응답하고, 각 question은 `passage_ref`(임시 id)로 참조한다. 한 시험 내에서만 dedup하며 시험 간 dedup은 안 한다 (출제 기록 성격).

### 4.10 questions ⭐ 문제 마스터
모든 문제의 단일 저장소. 1차 단계에서는 시험지 파일에서 추출된 문제만 INSERT되며 (`source=READFILE`, `exam_id` 채워짐), 카테고리별 취약점 분석의 기반이 된다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| question_id | BIGINT PK | |
| subject_id | INT FK | |
| category_id | BIGINT FK | nullable — 취약점 분석 집계 기준. **§5.0 4단계에서 자동 채워짐**: LLM이 응답한 `vocabulary`/`grammar`/`memorization`/`reading`/`other` 5종 중 하나를 `question_categories.category_name_en`로 lookup. 못 고르면 null. |
| passage_id | BIGINT FK | nullable — 독해 문제 등 공유 지문 참조. **§5.0 4단계에서 자동 채워짐**: LLM이 응답한 `passages` 마스터 배열을 먼저 INSERT한 뒤, 각 question의 `passage_ref`로 lookup해 매핑. 한 문제에만 딸린 짧은 본문은 `question_text` 안에 포함되어 별도 passage가 안 만들어진다. |
| exam_id | BIGINT FK | nullable — 시험지에서 추출된 문제 (`exams.exam_id`). 1차 단계에서는 항상 채워짐. |
| question_type | ENUM | MULTIPLE_CHOICE / SHORT_ANSWER |
| question_text | TEXT | 문제 본문 |
| choices | JSON | 객관식 보기 배열 (단답형이면 NULL) |
| correct_answer | VARCHAR(500) | 정답 |
| explanation | TEXT | 해설 (nullable) |
| source | ENUM | **READFILE** (1차: 파일 추출) / GENERATED (후속: LLM 생성) / MANUAL (후속: Admin 직접 입력) |
| order_num | INT | 세션 내 출제 순번 (정렬용) |
| question_label | VARCHAR(50) | nullable — 시험지에 적힌 그대로의 문항 라벨 (예: "1", "24", "[서답형 1] (1)"). 검증 페이지/표시용. 정렬은 `order_num`이 담당. |
| point | INT NOT NULL DEFAULT 0 | 문항 배점. LLM이 시험지의 "(3점)" 표기를 추출해 채움. 학생 검증 단계에서 수정 가능. **0이면 배점 미명시 문제** — `student_scores`의 점수 합산에서 제외하고 정답률만 표시한다. |

> 시험지는 `exam_id` 단위로 dedup되어 시험당 1세트만 존재 (§4 핵심 설계 원칙 4).
> 학생이 검증 단계에서 배점을 수정하면 **같은 `exam_id`를 공유하는 모든 학생의 questions에 반영**된다 (1차 단순화 — last-write-wins). 다만 다른 학생의 `student_scores`는 이미 확정 시점의 값으로 저장되어 있어 소급 변경되지 않는다.

### 4.11 student_answers ⭐ 학생 답안
1차 단계에서는 시험지 답안(`exam_id` 채워짐)만 들어온다. 후속 단계에서 진단/숙제 흐름이 도입되면 그쪽 답안도 본 테이블에 합쳐진다.
`questions.category_id`와 조인하면 카테고리별 취약점 집계가 가능하다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| answer_id | BIGINT PK | |
| student_id | BIGINT FK | |
| question_id | BIGINT FK | |
| exam_id | BIGINT FK | nullable — 시험지 답안일 때 (`exams.exam_id`). 1차 단계에서는 항상 채워짐. |
| student_answer | VARCHAR(500) | |
| is_correct | BOOLEAN | 자동 채점 결과 (학생 검증 단계에서 보정될 수 있음) |

**취약점 분석 쿼리 예시:**
```sql
-- 학생이 카테고리별로 몇 % 맞췄는지
SELECT q.category_id, COUNT(*) total, SUM(sa.is_correct) correct
FROM student_answers sa
JOIN questions q ON sa.question_id = q.question_id
WHERE sa.student_id = ?
GROUP BY q.category_id
```

### 4.12 student_scores ⭐ 시험지 점수

학생이 시험지를 업로드하면 LLM이 메타·문제·답안을 추출 → 학생이 메타+채점 검증 페이지(§5.0 5단계)에서 확정 → **확정 시점에 row 1개 INSERT**.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| score_id | BIGINT PK | |
| student_id | BIGINT FK | |
| exam_id | BIGINT FK | 어느 시험에 대한 점수인가 (`exams.exam_id`) — 시험 메타는 여기서 JOIN으로 조회 |
| source_id | BIGINT FK | 학생이 업로드한 시험지 파일(학생별 사본, sources 참조) |
| total_questions | INT | 총 문항 수 (배점 0 문제도 포함한 전체 카운트) |
| correct_count | INT | 정답 개수 (배점 0 문제도 포함) |
| total_points | INT | nullable — `questions.point` 합계. 신규 시험지부터 채워짐. NULL이면 구 데이터(정답률만 표시). 0이면 모든 문제 배점이 0(=배점 미명시 시험). |
| earned_points | INT | nullable — 맞은 문항의 `questions.point` 합계. `total_points`와 같이 NULL/non-NULL. |
| confirmed_at | DATETIME | 학생이 확정한 시각 |
| created_at / updated_at | TIMESTAMP | |

> **점수 표시 방식**: `total_points > 0`이면 **"75/100 (75.0%)"** 형식. `total_points`가 NULL이거나 0이면 정답률 **"8/10 (80.0%)"** 형식으로 폴백.
> 기존 PRD의 `student_scores` 부활본. 차이점: **수동 입력이 아니라 LLM 추출 + 학생 검수 방식**.
> 시험 메타(`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `exam_date`, `subject_id`)는 모두 `exams` 테이블에서 JOIN으로 조회한다. → §4.13 참고.

### 4.13 exams ⭐ 시험 마스터

학생 무관한 **시험 식별 단위**. 같은 (학교, 학년, 시험종류, 연도, 학기, 과목) 조합은 가능한 한 row 1개로 dedup 시도 — 단 메타 NULL 등으로 인해 **중복 row가 허용된다 (§4 핵심 설계 원칙 4)**.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| exam_id | BIGINT PK | |
| school_name | VARCHAR(100) NOT NULL | 예: "00중학교" |
| grade | TINYINT NOT NULL | 학년 1~6 |
| exam_type | ENUM NOT NULL | MIDTERM / FINAL / MOCK / OTHER |
| exam_year | SMALLINT | 연도 (nullable — 시험지에 없을 수도) |
| exam_semester | TINYINT | 학기 1 or 2 (nullable) |
| subject_id | INT FK NOT NULL | `subjects.subject_id` |
| display_name | VARCHAR(200) | 표시용 자동 조합값 (예: "00중학교 1학년 2024 1학기 중간고사") |
| exam_date | DATE | 시험 시행일 (nullable) |

**INDEX:** `idx_exams_dedup_key (school_name, grade, exam_type, exam_year, exam_semester, subject_id)` — **UNIQUE 아님**. dedup 검색 가속용. NULL 메타나 LLM 추출 편차로 동일 시험이 별도 row로 들어오는 것을 허용한다.

> dedup 매칭 흐름은 §5.0 4~6단계 참고 (학생 폼 메타 + LLM 유추 합성 → INDEX 검색 → 매치 시 재사용, 비매치 시 INSERT).
> 동일 시험의 중복 row 정리는 추후 **배치 dedup**(soft-merge via `canonical_exam_id`)에서 수행한다. 1차 미구현.


### 4.14 academies ✅

TUTOR가 소유하는 공개 강사/학원 페이지. 한 TUTOR는 Academy를 1개만 가진다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| academy_id | BIGINT PK | |
| tutor_user_id | BIGINT FK UNIQUE | `users.user_id`, `Role.TUTOR` 사용자 |
| slug | VARCHAR(100) UNIQUE | 공개 URL 식별자(한글 허용, 내부 식별·경로형 URL) |
| handle | VARCHAR(30) UNIQUE NULL | 영문 서브도메인 식별자 `{handle}.feel2real.co.kr`(V926). nullable=전환기 fallback(없으면 경로형 유지) |
| name | VARCHAR(100) | Academy 이름 |
| logo_storage_key | VARCHAR(500) | 필수 로고 이미지 저장 key. 사용자 업로드가 없으면 서버가 기본 로고를 저장해 채우며, API 응답에는 노출하지 않음 |
| cover_storage_key | VARCHAR(500) | 선택 커버 이미지 저장 key. API 응답에는 노출하지 않음 |
| principal_photo_storage_key | VARCHAR(500) NULL | 히어로(V8 시안) 원장 대표사진 저장 key(V940). logo/cover와 별개 슬롯 — 개별 관리 방침에 따라 재사용하지 않음. API 응답에는 노출하지 않음 |
| textbook_cover1~3_storage_key | VARCHAR(500) NULL | 히어로 대표 교재 표지 3장 저장 key(V940). 시안이 3장 고정 배치라 가변 테이블 대신 슬롯 컬럼 3개. API 응답에는 노출하지 않음 |
| schedule_image_storage_key | VARCHAR(500) NULL | 개설 시간표(`sec-lessons` 하단 `.lesson-schedule-inner`) 달력 이미지 저장 key(V948). **이 섹션의 유일한 본문**이라 값이 없으면 공개 페이지에서 시간표 섹션이 렌더되지 않는다. 수업상품에서 파생하던 행 목록(`.schedule-list`)이 달력형으로 보이지 않아 걷어내고 이 이미지로 대체했다. 히어로는 아니지만 단일 이미지 슬롯이라 업로드/서빙 경로를 공유한다(`HeroMediaSlot.SCHEDULE`). API 응답에는 노출하지 않음 |
| intro | TEXT | 소개문 |
| status | ENUM | DRAFT / PUBLISHED. 현재 생성 시 PUBLISHED |
| page_settings | JSON NULL | 공개페이지 표시전용 설정(blogUrl/storeUrl/naverMapUrl/heroYoutubeUrl/sectionVisibility/sectionOrder/builderDraft/businessInfo). 쿼리 대상 아님(V924). `sectionOrder`는 공개 섹션 전체 순서 배열이며 canonical default는 `sec-hero`, `sec-featured`, `sec-intro`, `sec-principal`, `sec-philosophy`, `sec-videos`, `sec-reviews`, `sec-lessons`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`, `sec-social`. hero는 항상 index 0. 저장 시 빠진 관리 섹션은 canonical 위치로 채워 완전한 순서로 정규화한다(옛/캐시된 클라이언트 호환 — 예전 size-lock 400 제거). `sec-featured`(시즌/Featured 프로그램)·`sec-digitaltwin`(AI Native 교육 과정)은 opt-in 섹션이라 `sectionVisibility[id] === true`일 때만 렌더되고(기본 OFF, 기존 사이트 불변), 나머지 데이터 섹션은 명시적 `false`일 때만 숨는다. builderDraft는 허용 key 기반 Builder 상세 소개/커리큘럼 문구와 히어로 지표(`monthlyTuition`, `scoreImprovement`, `operationYears`), Education Storefront 문구(`featured*`, `aiProcess*` — 재사용 가능한 generic key, 학교명·연도·학원명 하드코딩 없음), 섹션별 독립 제목(`videosSectionTitle`·`socialSectionTitle` — 미설정 시 학원명 파생값/고정 문구 fallback) key-value이며 홈페이지 업로드 영상 참조는 양의 정수 문자열 `heroVideoId`/`principalVideoId`로 저장한다. 가변 리스트(원장 약력 `principalCareer`, 학원 특징 `introFeatures`)는 개행 결합 문자열 1개 key로 저장하며 long key 상한은 2000자다. `businessInfo`의 직접 저장값은 상호명·대표·주소·사업자등록번호·통신판매업신고번호·전화·이메일 7개다. `businessInfoUrl` record field는 legacy JSON 호환용이며 server가 사업자등록번호 숫자 10자리에서 canonical FTC URL로 파생한다. Null/blank raw는 공개 홈·블로그 목록·상세에서 필드별 마스킹 dummy로 표시하되 저장값으로 역유입하지 않는다. Footer는 `sectionOrder`/`sectionVisibility` 밖 fixed page chrome이라 항상 ordered sections 다음에 존재한다. |

#### 시드 Academy (edu-holic)

`handle='edu-holic'`인 Academy는 V928이 시드한 데이터다. **특별 취급은 없다** — 렌더도 편집 권한도
다른 Academy와 완전히 동일하다.

과거에는 전용 정적 템플릿(`public/academy_edu_holic`)으로 서빙하고 소유자 편집도 403으로 막는
예외 케이스였으나, 히어로/학원 소개 개편에 방해가 되어 템플릿·분기·`DemoAcademy` 상수를 모두 제거했다.
시드 row 자체는 남아 있으므로 `is_visible=TRUE`로 `/academies` 공개 목록·사이트맵에 노출되며,
목록 클릭 시 `AcademyPathRedirectInterceptor`가 서브도메인으로 301 한다.

#### ALB 요청 기반 학원 접근 통계 (V942)

`handle`은 사용자가 바꿀 수 있으므로 통계의 영속 식별자로 쓰지 않는다. ALB 로그의 요청 시점
`{handle}.feel2real.co.kr`을 `academy_handle_histories`의 유효기간으로 `academy_id`에 매핑한 뒤,
누적과 KST 일별 요청 수를 별도 테이블에 적재한다. Academy 삭제 시 관련 통계와 handle 이력은
`ON DELETE CASCADE`로 삭제한다.

| 테이블 | 핵심 컬럼 | 설명 |
|---|---|---|
| `academy_handle_histories` | `academy_id`, `handle`, `valid_from_utc`, `valid_to_utc` | handle 변경 전후 로그를 같은 Academy로 귀속시키는 유효기간 이력 |
| `academy_access_statistics` | `academy_id` PK, `total_request_count`, `last_log_at` | 성공한 공개 루트 문서 요청의 누적값 |
| `academy_access_daily_statistics` | `(academy_id, access_date)` PK, `request_count` | `Asia/Seoul` 날짜별 요청 수 |
| `alb_access_log_imports` | `object_key_hash` PK, `object_key`, `etag`, `imported_request_count` | 같은 S3 ALB 로그 객체 재처리 시 중복 가산을 막는 처리 원장 |

집계 대상은 ALB status `200~399`인 `GET https://{handle}.feel2real.co.kr/` 요청이다. query string은
허용하지만 정적 파일·하위 경로·실패 응답은 제외한다. IP, user-agent, 로그인 ID는 저장하지 않는다.
이 값은 **요청 수**이며 사람 수·순방문자·세션 수가 아니다.

### 4.15 academy_subjects ✅

Academy ↔ 과목 M:N 매핑. 현재 UI는 단일 선택처럼 동작하지만 schema는 복수 과목을 허용한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| academy_id | BIGINT FK | `academies.academy_id` |
| subject_id | INT FK | `subjects.subject_id` |

### 4.16 academy_target_groups ✅

Academy 대상 학생군.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| academy_id | BIGINT FK | |
| target_group | ENUM | ELEMENTARY / MIDDLE / HIGH / UNIVERSITY / ADULT |

### 4.17 academy_regions ✅

Academy 운영 지역. 최대 3개까지 입력한다. `district='ALL'`이면 해당 city 전 지역을 뜻한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| region_id | BIGINT PK | |
| academy_id | BIGINT FK | |
| city | VARCHAR(50) | 예: 서울시 |
| district | VARCHAR(50) | 예: 서초구, 또는 ALL |
| display_name | VARCHAR(100) | 예: 서울시 전 지역 / 서울시 서초구 |

**UNIQUE:** `(academy_id, city, district)`

### 4.18 lesson_products ✅

Academy에 노출되는 수업 상품. “주 3회”, “한 달”, “100만원 이하”, “가능 요일” 같은 후속 검색 필터를 위해 가격/일정/요일을 구조화해서 저장한다. V931은 기존 row를 backfill하지 않고 전용 편집기의 공개 문구/theme/생성 key/작성 revision 컬럼을 nullable로 추가했다. V944는 LMS 강좌관리 표와 편집기가 공유하는 학교·학년 자유문구를 nullable로 추가하며 기존 row는 backfill하지 않는다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| lesson_id | BIGINT PK | |
| academy_id | BIGINT FK | `academies.academy_id` |
| name | VARCHAR(100) | 수업명 |
| description | TEXT | 수업 설명 |
| school_name | VARCHAR(100) NULL | 학교 자유문구. blank는 null로 정규화하며 학교 master와 연결하지 않는다 |
| grade_label | VARCHAR(50) NULL | 학년 자유문구. blank는 null로 정규화하며 enum/filter 계약을 만들지 않는다 |
| lesson_type | ENUM NULL | SUBSCRIPTION / SHORT_TERM. V932에서 NOT NULL 해제 — 편집기가 받지 않는다 |
| target_group | ENUM NULL | Academy `TargetGroup`과 동일: ELEMENTARY / MIDDLE / HIGH / UNIVERSITY / ADULT. V927이 기존 데이터를 HIGH로 backfill한 뒤 NOT NULL DEFAULT 'HIGH'로 두었으나, V932에서 제약과 기본값을 모두 해제 — 지정하지 않은 대상이 공개 카드·검색 필터에 노출되지 않게 한다 |
| price_amount | INT | 가격. `price_unit=NEGOTIABLE`이면 NULL 가능 |
| price_unit | ENUM NULL | MONTH / COURSE / NEGOTIABLE. V932에서 NOT NULL 해제 |
| price_summary | VARCHAR(100) NULL | 표시용 가격 문구. V932에서 NOT NULL 해제 |
| max_capacity | INT NULL | 정원. V932에서 NOT NULL 해제. **NULL은 무제한 접수**를 뜻하며 `isFull()`은 false, `remainingCapacity()`는 null |
| current_count | INT | 현재 승인 신청 인원. `lesson_applications.status=APPROVED` 신청/취소와 함께 증감 |
| schedule_type | ENUM NULL | WEEKLY / PERIOD / CONSULTATION. V932에서 NOT NULL 해제 |
| weekly_count | INT | 주간 수업 횟수 |
| start_date | DATE | 기간형 시작일 |
| end_date | DATE | 기간형 종료일 |
| duration_weeks | INT | 기간형 기간(주) |
| duration_months | INT | 기간형 기간(개월) |
| schedule_summary | VARCHAR(200) NULL | 표시용 일정 문구. V932에서 NOT NULL 해제 |
| status | ENUM | DRAFT / PUBLISHED / HIDDEN |
| is_visible | BOOLEAN | 공개 노출 여부. Admin visibility 변경과 신청 인원 변경은 authoring revision에 영향 없음 |
| tab_guidance_label | VARCHAR(120) NULL | 편집기 탭 안내 문구 |
| tab_short_name | VARCHAR(80) NULL | 편집기 탭 짧은 이름 |
| kicker | VARCHAR(200) NULL | 마케팅 상단 보조 문구 |
| highlight | VARCHAR(300) NULL | 핵심 강조 문구 |
| learner_need | VARCHAR(300) NULL | 학습자 요구 문구 |
| core_detail_title | VARCHAR(200) NULL | 핵심 상세 제목 |
| explanation_1 | VARCHAR(500) NULL | 상세 설명 1 |
| explanation_2 | VARCHAR(500) NULL | 상세 설명 2 |
| explanation_3 | VARCHAR(500) NULL | 상세 설명 3 |
| theme_key | VARCHAR(20) NULL | NAVY / TEAL / PURPLE / ORANGE / SKY / PINK / GREEN. 레거시 NULL 응답 기본값은 NAVY |
| creation_key | VARCHAR(36) NULL | 편집기 신규 항목의 안정적 UUID 키. Academy 범위에서만 유일 |
| authoring_revision | BIGINT NULL | 작성 필드 낙관적 revision. 레거시 NULL의 유효값은 0 |
| media_image_key | VARCHAR(500) NULL | 대표 미디어 이미지의 FileStorage key. `media_video_id`와 배타 |
| media_video_id | BIGINT NULL FK | 대표 미디어 영상 `academy_videos.video_id`. `media_image_key`와 배타 |
| ot_video_id | BIGINT NULL FK | 수업 OT 영상 `academy_videos.video_id`. 대표 미디어와 독립 |

**UNIQUE/INDEX:**
- `uk_lesson_products_academy_creation_key (academy_id, creation_key)` — nullable 복합 UNIQUE. 서로 다른 Academy는 같은 creation key를 사용할 수 있음
- `idx_lesson_products_media_video (media_video_id)`
- `idx_lesson_products_ot_video (ot_video_id)`
- `fk_lesson_products_media_video` → `academy_videos(video_id)` **ON DELETE SET NULL**
- `fk_lesson_products_ot_video` → `academy_videos(video_id)` **ON DELETE SET NULL**

**대표 미디어(히어로) 규칙:**
- **XOR 불변식**: `media_image_key`와 `media_video_id`는 동시에 non-null일 수 없다. 둘 다 null이면 "미디어 없음"이다. 엔티티(`updateAuthoringDetails`)와 collection 저장 양쪽에서 검증한다.
- **OT 영상은 별도 객체**: `ot_video_id`는 대표 이미지/영상과 독립적으로 저장·교체·해제한다. 대표 미디어 XOR에 포함하지 않으며 같은 학원 소유 영상만 참조할 수 있다.
- **`media_type` 컬럼은 없다.** 위 두 컬럼에서 파생하며 응답 DTO(`LessonResponse.mediaType`)에서만 표현한다. 컬럼으로 만들면 동기화 지점만 늘어난다.
- **영상은 학원 풀 공유**: durable upload 예약과 외부 링크 영상(`source_kind='EXTERNAL_LINK'`)을 포함해 Academy당 최대 30개다. `academy_videos`에 `lesson_id`를 두는 양방향 참조는 쓰지 않는다 — 두 컬럼이 서로 다른 동시성 계약으로 쓰이는데 화해자가 없어 드리프트가 남는다.
- **FK가 RESTRICT가 아닌 이유**: `lesson_products`(V3)와 `academy_videos`(V930)가 **둘 다** `academies`에서 `ON DELETE CASCADE`다. 여기에 RESTRICT를 걸면 InnoDB가 형제 캐스케이드 순서를 보장하지 않아 `DELETE FROM academies`가 errno 1451로 실패한다. 회원 탈퇴(`UserService.withdraw()`)는 `users → academies` 2단계라 함께 깨지고, `Academy`에 `@OneToMany`가 없어 JPA로 순서를 조정할 seam도 없다.
- **사용자 대면 삭제 차단은 서비스 계층**: `AcademyVideoService.delete()`가 대표 미디어 또는 OT 영상으로 참조 중인 수업상품이 있으면 `400`으로 막는다. 그 판정이 read-then-write라 해당 메서드만 `@Transactional`이고 참조 행에 락을 잡는다. FK는 최후 방어선일 뿐이다.
- **silent detach 경로**: 서비스를 우회한 직접 SQL로 영상 row를 지우면 참조가 조용히 null이 되어 "미디어 없음"으로 degrade된다. 현재 프로덕션 경로 중 그런 것은 없다(학원/회원 삭제는 두 테이블을 함께 캐스케이드하고, `db/dev-seed/R__*`에는 DELETE/TRUNCATE가 없다). 네 번째 경로를 추가하지 말 것.
- **공개 노출**: 이미지 storage key는 공개 응답에 싣지 않는다. `GET /api/academies/{slug}/lessons/{id}/hero-image`로만 나간다. 대표 영상과 별도 OT 영상은 각각 썸네일만 렌더하고 클릭 시에만 `/stream`을 붙인다(`app.video.serve-permits`=4가 전체 공개 트래픽 상한이다).
- 공개 학원 페이지의 영상 섹션도 최대 10개이며, 대표 영상은 `videoId`로 같은 Academy 영상 풀을 직접 참조한다.

**작성 상태/revision 규칙:**
- canonical 작성 상태는 운영 필드(이름/설명/**학교·학년**/유형/대상/가격/정원/일정), 공개 문구, theme, status를 포함한다. `current_count`, `is_visible`, audit timestamp, `creation_key`, revision 자체는 제외한다. 학교·학년만 달라져도 변경으로 분류해 revision을 한 번 증가시키며 trim 후 같은 값 또는 blank→null 재시도는 replay다.
- collection 신규 생성은 `authoring_revision=1`로 시작한다. 기존 수업은 canonical 작성 상태 또는 hide 상태가 실제로 달라질 때 한 번 증가한다. 상태가 같은 재시도는 증가하지 않는다.
- 신청/취소의 `current_count`와 Admin의 `is_visible` 변경은 revision 중립이다. Tutor hide는 `status=HIDDEN` 변경 때문에 처음 한 번만 증가한다.
- nullable 레거시 `theme_key`/`authoring_revision`은 읽을 때 각각 NAVY/0으로 해석한다. 저장 요청에 포함된 수업만 lazy normalization하며, 요청에서 빠진 수업은 수정하지 않는다.
- `creation_key`는 신규 draft 재시도 식별자다. 같은 Academy 안에서 같은 key와 같은 canonical 상태는 replay로 처리하고, 다른 상태면 binding conflict로 거부한다.
- 강사용 `LessonResponse`는 non-null 학교·학년을 반환하지만 공개 `LessonResponse.fromPublic()`은 두 property 자체를 생략한다. LMS 강좌관리 API는 별도 최소 DTO만 사용한다.

**기존 row writer 잠금 규칙:**
- collection 저장은 creation-key binding을 entity가 아닌 scalar projection으로 해석하고, 대상 lesson ID 합집합을 `lesson_id ASC` 순서의 `PESSIMISTIC_WRITE` query로 처음 hydrate한다. 잠금 뒤에 revision/canonical 상태를 비교한다.
- legacy 단일 PUT, Tutor hide, Admin visibility도 `findForUpdateByLessonId`로 같은 row lock을 획득한다.
- 학생 신청/취소는 기존 pessimistic lock을 유지한다. 이 writer들과 Admin visibility는 revision을 올리지 않지만, 동일 row lock으로 작성 저장과의 덮어쓰기를 막는다.

### 4.19 lesson_weekdays ✅

수업 상품의 반복 요일.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| lesson_id | BIGINT FK | `lesson_products.lesson_id` |
| weekday | ENUM | MONDAY / TUESDAY / WEDNESDAY / THURSDAY / FRIDAY / SATURDAY / SUNDAY |

---


### 4.20 lesson_applications ✅

학생이 공개 수업 상품에 신청한 상태. MVP는 강사 승인/거절 없이 정원 검증 후 즉시 승인한다. 같은 학생이 같은 수업에 중복 신청하지 못하도록 1 row만 유지한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| application_id | BIGINT PK | |
| lesson_id | BIGINT NOT NULL FK | `lesson_products.lesson_id` |
| student_id | BIGINT NOT NULL FK | `students.student_id` |
| conversation_id | BIGINT NULL FK | `conversations.conversation_id`. 수업 신청 시 연결된 학습 대화 (V9 추가). nullable — 신규 신청 시 자동 생성되거나 학생이 선택. ON DELETE SET NULL. |
| status | ENUM | APPROVED / CANCELED. PENDING/REJECTED는 MVP 제외 |
| applied_at | TIMESTAMP NOT NULL | 신청/재신청 시각 |
| canceled_at | TIMESTAMP | 취소 시각 |

**UNIQUE/INDEX:**
- `UNIQUE (student_id, lesson_id)` — 동일 학생의 동일 수업 중복 신청 방지
- `INDEX (student_id, status)` — 학생 신청 목록
- `INDEX (lesson_id, status)` — 수업별 승인 인원/강사 목록

**상태/매핑 규칙:**
- 신청 성공: 정원 검증 후 즉시 `APPROVED`, `lesson_products.current_count += 1`. `conversationId`를 전달하면 소유 학생 검증 후 연결, null이면 "{수업명} 학습 대화" 제목으로 자동 생성.
- 중복 신청: 기존 row가 `APPROVED`면 거부. `CANCELED`면 같은 row를 `APPROVED`로 재활성화 + conversation 연결.
- 취소: `CANCELED`, `current_count -= 1`. conversation 연결은 이력 보존을 위해 유지 (끊지 않음).
- **TUTOR 권한 가드 SoT**: `lesson_applications.status=APPROVED` 기반 (`tutor_student_map` 호출 제거 — V9 이후).

### 4.21 tutor_student_map ⭐ 강사-학생 매핑 (Phase 2 부활)

원래 PRD에서 제거됐다가 Phase 2 §5.5에서 부활. TUTOR가 학생 conversation·자료에 접근할 때의 권한 가드 기반이다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| mapping_id | BIGINT PK | |
| tutor_user_id | BIGINT NOT NULL FK | `users.user_id` (TUTOR role) — `academies.tutor_user_id`와 명명 통일 |
| student_id | BIGINT NOT NULL FK | `students.student_id` |
| is_active | BOOLEAN NOT NULL DEFAULT TRUE | 활성 매핑만 가드 통과 |

**INDEX/UNIQUE:**
- `UNIQUE (tutor_user_id, student_id)` — 동일 쌍 중복 금지
- `INDEX (tutor_user_id, is_active)` — 강사 진입 시 매핑 학생 조회
- `INDEX (student_id, is_active)` — 학생 자료 접근 시 매핑 강사 조회

> Phase 2 단순화:
> - 과목 분리 안 함 (MVP는 영어 단일). 다과목 도입 시 별도 매핑 테이블이 더 깔끔.
> - 매핑 자체의 CRUD UI는 미구현 — `db/dev-seed/V901`로만 시드 (또는 운영에서 SQL 직접). 추후 admin UI 추가.
> - 3차 매칭 플랫폼화(§0)에서 자동 매칭 흐름으로 대체될 예정.
>
> **⚠️ V9 이후 변경사항**: TUTOR 권한 가드의 SoT가 `tutor_student_map.is_active` → `lesson_applications.status=APPROVED`로 이전됨 (`AuthenticatedUserResolver` 교체). `activate/deactivateTutorStudentMap` 호출은 `LessonApplicationService`에서 제거됨. **테이블/엔티티/레포는 유지** — 별도 cleanup PR에서 DROP 예정.

### 4.22 learning_materials ⭐ 학습자료 (Phase 2)

Admin 또는 TUTOR가 학생에게 부여한 학습자료. 한 conversation 컨텍스트 안에서 부여한다. 문제 PDF는 **선택**이며, 없으면 title·description만으로 부여할 수 있다(V13).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| material_id | BIGINT PK | |
| student_id | BIGINT NOT NULL FK | `students.student_id` |
| conversation_id | BIGINT NOT NULL FK | `conversations.conversation_id` — 부여 컨텍스트 |
| problem_source_id | BIGINT **NULL** FK | `sources.source_id`. `source_type=HOMEWORK_PROBLEM`, `file_type=UNKNOWN`(raw PDF). **V13에서 NOT NULL → NULL 허용** — PDF 없이 부여 시 NULL (nullable FK) |
| assigned_by | BIGINT NOT NULL FK | `users.user_id` (Admin 또는 TUTOR). role 무관 |
| title | VARCHAR(200) NOT NULL | 학생에게 보이는 제목 |
| description | TEXT | 안내 메시지 (nullable) |
| status | ENUM | `ASSIGNED` / `COMPLETED`. 학생이 풀이 결과를 올리면 COMPLETED로 전이 |
| assigned_at | DATETIME NOT NULL | 부여 시각 |
| completed_at | DATETIME | 풀이 결과 업로드 시각 |
| **reviewed** | BOOLEAN NOT NULL DEFAULT FALSE | 강사/Admin 확인(채점 완료) 여부 (V11) |
| **reviewed_at** | DATETIME NULL | 확인 시각 (V11) |
| **reviewed_by** | BIGINT NULL FK | `users.user_id` ON DELETE SET NULL — 확인한 사용자 (V11) |

**INDEX:** `idx_learning_materials_student_status (student_id, status)`, `idx_learning_materials_conversation (conversation_id)`

> 문제 PDF는 `FileStorage.saveRaw`로 raw 보관 → 학생이 PDF로 그대로 다운로드 가능. LLM 분석은 안 함.
>
> **PDF 없는 부여 (V13)**: `assign`에서 `file`이 없으면 `problem_source_id=NULL`로 저장하고 source 생성/`conversation_sources` 링크를 스킵한다. 이 숙제는 `/view`·`/download`가 404를 반환하므로 프론트는 응답의 `hasProblemFile=false`로 문제 PDF 뷰어/다운로드 버튼을 숨기고 description을 본문에 노출한다.
>
> **`reviewed` 상태 머신**: 강사/Admin이 `PATCH /api/learning-materials/{id}/reviewed` body `{reviewed:true}`로 확인 완료 표시. 학생이 재제출(`complete`)하면 자동으로 `reviewed=false` 리셋.

### 4.23 learning_material_answers ⭐ 학생 풀이 결과물 매핑 (Phase 2)

한 학습자료에 학생이 올린 풀이 파일(이미지/PDF) 매핑.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| answer_id | BIGINT PK | |
| material_id | BIGINT NOT NULL FK | `learning_materials.material_id` (ON DELETE CASCADE) |
| source_id | BIGINT NOT NULL FK | `sources.source_id`. `source_type=HOMEWORK_ANSWER`, `file_type=UNKNOWN` |
| order_num | INT NOT NULL | 업로드 순서 (1부터) |

**INDEX:** `idx_learning_material_answers_material (material_id)`

> 재업로드는 row 전체 삭제 후 재INSERT (대치 모드). 부분 수정 안 함 — UX/구현 모두 단순.
>
> **conversation_sources 연동 (PR1)**: 업로드 시 `source_type=HOMEWORK_ANSWER` source를 `conversation_sources`에 링크 + `metadata` SourceSummary append → AI Chat 접근 가능. 재업로드 시 기존 answer source 링크 먼저 제거(FK-safe) 후 신규 링크.

### 4.24 learning_material_feedbacks ✅ 강사 피드백 매핑 (V11 스키마, PR2 엔티티/엔드포인트 구현)

강사가 학생 풀이에 붙여주는 채점·피드백 PDF 매핑. 스키마(V11) + JPA 엔티티(`LearningMaterialFeedback`) + 엔드포인트 모두 구현 완료.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| feedback_id | BIGINT PK | |
| material_id | BIGINT NOT NULL FK | `learning_materials.material_id` (ON DELETE CASCADE) |
| source_id | BIGINT NOT NULL FK | `sources.source_id`. `source_type=HOMEWORK_FEEDBACK` (ON DELETE CASCADE) |
| order_num | INT NOT NULL | 업로드 순서 (1부터) |
| created_at / updated_at / created_by / updated_by | BaseEntity 감사 컬럼 | |

**INDEX:** `idx_learning_material_feedbacks_material (material_id)`

> `learning_material_answers` 구조를 미러. `source_type=HOMEWORK_FEEDBACK`(V11 추가)인 source를 material에 연결.
> PR2 구현 완료: `LearningMaterialFeedback` 엔티티, 피드백 업로드(`POST .../feedback`) / 미리보기(`GET .../feedback/{fid}/file`) / 삭제(`DELETE .../feedback/{fid}`) 엔드포인트.
> 피드백 source는 `conversation_sources`에 편입 — AI Chat 접근 가능. 학생 재제출 시 기존 피드백 전체 삭제(stale 정리). material 삭제 시 피드백 source/storage/링크도 cascade 정리.

---


### 4.25 academy_blog_posts ✅

Academy별 공개/비공개 블로그 글. 공개 상태(`PUBLISHED` + `is_visible=true`)이고 Academy도 공개 상태인 글만 public API와 `/academy-posts`에 노출한다.

| column | type | note |
|---|---|---|
| post_id | BIGINT PK | |
| academy_id | BIGINT FK | `academies.academy_id` |
| title | VARCHAR(200) | |
| slug | VARCHAR(160) | Academy 내 unique |
| summary | VARCHAR(500) | |
| content_json | LONGTEXT | Tiptap JSON 원본 |
| content_html | LONGTEXT | sanitizer 적용 HTML |
| meta_title / meta_description | VARCHAR | SEO 메타 |
| status | ENUM | `DRAFT`, `PUBLISHED` |
| is_visible | BOOLEAN | public 노출 여부 |
| published_at | TIMESTAMP nullable | 발행 시각 |

### 4.26 academy_blog_source ✅

Academy 블로그 본문 이미지 메타데이터. 기존 FileStorage를 재사용하고, public 글에 연결된 이미지는 public inline 조회를 허용한다.

| column | type | note |
|---|---|---|
| source_id | BIGINT PK | |
| academy_id | BIGINT FK | `academies.academy_id` |
| post_id | BIGINT nullable FK | 작성 중 이미지는 null 가능 |
| source_type | ENUM | 현재 `IMAGE` |
| original_filename / stored_filename | VARCHAR | |
| storage_key | VARCHAR(500) | FileStorage key |
| file_url | VARCHAR(500) | public image URL |
| content_type | VARCHAR(100) | sanitizer 후 `image/jpeg` |
| file_size | BIGINT | bytes |
| sort_order | INT | 본문 삽입 순서 |

### 4.27 academy_blog_post_likes ✅

공개 Academy 블로그 글 추천/좋아요. 로그인 사용자만 누를 수 있고 사용자별 글 1회 unique 제약을 둔다. 다시 누르면 row를 삭제해 취소한다. 추천 가능 기간은 application 설정 `app.academy-blog.like-allowed-days` 기본 30일이다.

| column | type | note |
|---|---|---|
| like_id | BIGINT PK | |
| post_id | BIGINT FK | `academy_blog_posts.post_id`, cascade delete |
| user_id | BIGINT FK | `users.user_id`, cascade delete |
| created_at / updated_at | TIMESTAMP | 추천 시각 / 감사 |

제약/인덱스:
- `UNIQUE(post_id, user_id)`로 사용자별 1회 제한
- `(post_id, created_at)` 인덱스로 최근 추천순 집계

### 4.28 academy_page_layouts ✅ (Academy Builder Phase 1)

Academy 한 곳당 1개의 페이지 레이아웃을 보존한다(Academy Builder 토대). `layout_json`은 raw HTML/CSS가 아닌 **allowlist 기반 semantic JSON**만 담는다. Academy 삭제 시 FK `ON DELETE CASCADE`로 함께 제거된다. (V921)

| column | type | note |
|---|---|---|
| layout_id | BIGINT PK | |
| academy_id | BIGINT FK UNIQUE | `academies.academy_id`, cascade delete. 1 Academy ↔ 1 layout |
| layout_version | INT NOT NULL | 스키마 버전, 기본 1 |
| layout_json | LONGTEXT NOT NULL | semantic 레이아웃 직렬화 JSON |
| created_at / updated_at / created_by / updated_by | | 감사 |

제약/인덱스:
- `UNIQUE(academy_id)`로 Academy당 레이아웃 1건 보장

**Semantic Layout Contract** — `layout_json`은 `{ "version": int, "blocks": [ { "type", "order", "config" } ] }` 형태. `type`은 allowlist 만 허용:
`topNav`, `hero`, `quote`, `teachers`, `curriculum`, `consultationCta`, `blogPreview`. 자유 HTML/CSS·기타 타입은 거부된다. 기본 레이아웃 block 순서는 위 나열 순. `consultationCta`는 Phase 1에서 `state=PREPARING, enabled=false`(결제/CRM 액션 타깃 없음), 아직 콘텐츠 없는 영역(teachers 등)은 `placeholder=true`. 텍스트 값은 user-generated content로 보고 공개 렌더 단계에서 escape/sanitize한다.

**Legacy fallback (Phase 2):** `AcademyPageLayoutService.resolveLayoutSchema(academy)`는 저장 레이아웃이 있으면 역직렬화해 반환하고, 없으면(레이아웃 생성 전 레거시 Academy) `AcademyDefaultLayoutFactory`로 기본 레이아웃을 결정적으로 생성해 반환한다. read-only(저장 안 함) — 공개 렌더가 레거시 row도 안전하게 그릴 수 있게 한다.

**공개 렌더 (Phase 3):** `AcademyLayoutPageController`가 `GET /academies/{slug}/preview`에서 `resolveLayoutSchema`로 얻은 block을 order대로 Thymeleaf(`public/academy_layout_preview`)로 SSR한다. 기존 CSR 페이지(`/academies/{slug}`)는 그대로 두고 신규 경로로 병행한다. block 텍스트는 `th:text`로 escape하며, `placeholder=true`·`consultationCta.state=PREPARING`은 안전한 안내 영역으로 렌더한다. `blogPreview`는 `AcademyBlogService.listPublic(slug)`을 config `limit`개로 잘라 노출한다.

**생성/수정 API (Phase 4 백엔드):** `AcademyLayoutApiController`가 `GET/PUT/DELETE /api/tutor/academy/layout`로 본인 Academy 레이아웃을 조회/upsert/삭제한다(owner guard = `AcademyService.requireMyAcademy`). PUT은 전체 schema upsert(`saveLayout`, 1 Academy 1 레이아웃, allowlist 위반 400), DELETE는 물리 삭제 후 기본 레이아웃 fallback. 빌더 UI(화면단)는 이 API를 호출하는 별도 작업으로 이어진다.

> Phase 1 범위: 보존 모델 + 기본 레이아웃 생성기 + allowlist 검증. Phase 2: legacy fallback resolver. Phase 3: 공개 렌더(SSR, 신규 라우트 병행). Phase 4: 생성/수정/삭제 API. 빌더 UI(Thymeleaf+JS)는 이후 작업.

### 4.30 lesson_community_categories ✅

Lesson 단위 커뮤니티의 강사/Admin 생성 커스텀 탭. 기존 DB 호환상 테이블명은 categories를 유지하지만 UI 용어는 “탭”으로 통일한다. 기본 탭(`전체글`, `일반글`, `Q&A`, `공지`)은 코드/쿼리로 제공하며 이 테이블에는 커스텀 탭만 저장한다.

| column | type | note |
|---|---|---|
| category_id | BIGINT PK | |
| lesson_id | BIGINT FK | `lesson_products.lesson_id` |
| name | VARCHAR(50) | lesson 내 unique |
| sort_order | INT | 기본 0 |
| is_visible | BOOLEAN | 레거시 호환 필드. 현재 탭 삭제는 하드 삭제이며 삭제 전 글은 일반글로 이동 |

### 4.31 lesson_community_posts ✅

Lesson 단위 커뮤니티 게시글. 일반글/Q&A/공지 유형을 가진다.

| column | type | note |
|---|---|---|
| post_id | BIGINT PK | |
| lesson_id | BIGINT FK | `lesson_products.lesson_id` |
| category_id | BIGINT nullable FK | `lesson_community_categories.category_id` |
| author_user_id | BIGINT FK | 작성자 `users.user_id` |
| post_type | VARCHAR(20) | `GENERAL`, `QNA`, `NOTICE` |
| title | VARCHAR(150) | |
| content | TEXT | 최대 5000자 app validation |
| qna_public | BOOLEAN | Q&A 공개 여부. Q&A가 아니면 false |
| is_visible | BOOLEAN | 삭제는 물리 삭제가 아니라 false 숨김 |
| hidden_at / hidden_by_user_id | DATETIME / BIGINT | 숨김 처리 감사 정보 |

권한 정책:
- lesson 소유 강사, 해당 lesson APPROVED 학생, ADMIN만 접근한다.
- Q&A 비공개 글은 작성 학생 + lesson 소유 강사 + ADMIN만 읽는다.
- 글 수정은 작성자만 가능하다.
- 글 숨김은 작성자, lesson 소유 강사, ADMIN이 가능하다.

### 4.32 lesson_community_comments ✅

Lesson community 게시글의 댓글. 부모 글 읽기 권한과 동일한 범위에서 노출한다.

| column | type | note |
|---|---|---|
| comment_id | BIGINT PK | |
| post_id | BIGINT FK | `lesson_community_posts.post_id` |
| author_user_id | BIGINT FK | 작성자 |
| content | TEXT | 최대 5000자 app validation |
| is_visible | BOOLEAN | 삭제는 물리 삭제가 아니라 false 숨김 |
| hidden_at / hidden_by_user_id | DATETIME / BIGINT | 숨김 처리 감사 정보 |

댓글 수정은 작성자만 가능하고, 숨김은 작성자/lesson 소유 강사/ADMIN이 가능하다.

### 4.33 lesson_community_attachments ✅

Lesson community 전용 첨부파일. conversation source/homework와 자동으로 섞지 않는다.

| column | type | note |
|---|---|---|
| attachment_id | BIGINT PK | |
| post_id | BIGINT nullable FK | 게시글 첨부일 때 사용 |
| comment_id | BIGINT nullable FK | 댓글 첨부일 때 사용 |
| uploader_user_id | BIGINT FK | 업로더 |
| original_file_name | VARCHAR(255) | 원본 파일명 |
| content_type | VARCHAR(100) | 응답 Content-Type |
| file_size | BIGINT | bytes |
| storage_key | VARCHAR(500) | FileStorage key |
| is_visible | BOOLEAN | 부모 글/댓글 숨김 시 UI 미노출 |

첨부는 글/댓글 작성 시 최대 10개까지 저장한다. 파일 용량/이미지 degrade 정책은 서비스 공통 업로드 정책 후속으로 분리한다.

### 4.34 community_posts ✅ 공개 커뮤니티 질문(Question) (V914 → V915 Q&A전환 → V916 게시판모델)

lesson_community와 별개의 **공개 Q&A 공간**(`/community`). 2계층: 질문(community_posts) → 답변(answers). (V917에서 댓글 폐기 — 답변과 겹침.) 읽기는 비로그인 포함 누구나, 작성/답변/좋아요=로그인, 수정=작성자, 삭제=작성자/Admin soft hide. (테이블명은 레거시 호환으로 community_posts/post_id 유지 — 의미상 Question.)

**게시판 모델(V916)**: dev-topic 카테고리 enum을 폐기하고 `board_type` + `subject_id`로 전환. 탭 = 전체(필터 없음) / 자유게시판(`FREE`) / Q&A(`QNA`) / 과목(`SUBJECT`, `subjects` 테이블 동적 참조 — 현재 영어, 추후 국어·수학 등). `SUBJECT`일 때만 `subject_id` 세팅, 그 외 NULL.

| column | type | note |
|---|---|---|
| post_id | BIGINT PK | =question id |
| author_user_id | BIGINT FK users NOT NULL | 작성자 |
| board_type | VARCHAR(20) NOT NULL | enum name() 저장. F2R 커뮤니티: `FREE`(자유게시판) / `QNA`(Q&A) / `SUBJECT`(과목). 공개 BuildIt Wiki(/wiki): `EXAM_REVIEW`(시험 후기) / `VARIANT`(변형문제) / `MATERIAL`(자료 공유·요청). 같은 테이블, 목록은 `scope`(community/wiki)로 분리. enum 값 추가는 마이그레이션 불필요(CHECK 없음). UI에 한글 라벨 |
| subject_id | INT NULL FK subjects(subject_id) | `board_type=SUBJECT`일 때만. UI 라벨=과목 한글명(name_ko) |
| title | VARCHAR(150) NOT NULL | **keyword 검색 대상(lower(title))** |
| content_json | LONGTEXT NOT NULL | Tiptap 원본 JSON |
| content_html | LONGTEXT NOT NULL | sanitize된 HTML. ⚠ LOB라 lower() 검색 금지(FunctionArgumentException) → keyword는 title만 |
| is_visible | BOOLEAN NOT NULL DEFAULT TRUE | soft hide |
| hidden_at / hidden_by_user_id | DATETIME(6) / BIGINT FK users NULL | 숨김 시각/주체 |
| (BaseEntity) | created_at/updated_at/created_by/updated_by | |

인덱스: `idx_community_posts_board_subject_visible(board_type, subject_id, is_visible, created_at, post_id)`(게시판/과목 탭+최신), `idx_community_posts_visible_created(is_visible, created_at, post_id)`, `idx_community_posts_author`.

### 4.35 answers ✅ 질문에 대한 답변 (V914)

질문당 N개. Tiptap 본문. 작성=로그인, 수정=작성자, 삭제=작성자/Admin soft hide.

| column | type | note |
|---|---|---|
| answer_id | BIGINT PK | |
| question_id | BIGINT FK community_posts(post_id) (ON DELETE CASCADE) | soft-hide라 CASCADE 휴면 |
| author_user_id | BIGINT FK users NOT NULL | |
| content_json / content_html | LONGTEXT | Tiptap, sanitize |
| is_visible / hidden_at / hidden_by_user_id | | soft hide |
| (BaseEntity) | | |

인덱스: `idx_answers_question_visible_created(question_id, is_visible, created_at)`(답변 목록), `idx_answers_author`.

### 4.36 community_comments ❌ 폐기 (V914 생성 → V917 DROP)

공개 커뮤니티는 **질문→답변 2계층**으로 단순화(댓글 폐기). 답변(Tiptap 서식/이미지)이 plain text 댓글과 기능상 겹쳐 댓글 계층 제거. V917에서 `DROP TABLE community_comments`. (lesson_community_comments는 별개 도메인 — 유지.)

### 4.37 community_post_likes ✅ 질문 좋아요(추천) (V914)

질문에만. 사용자-질문당 1회(UNIQUE), 토글. 작성 후 N일(`app.community.like-allowed-days`, 기본 30, **운영자 설정 변경 가능**) 이내만. 추천순=최근 `app.community.recommendation-window-days`(기본 7)일 좋아요 수.

| column | type | note |
|---|---|---|
| like_id | BIGINT PK | |
| post_id | BIGINT FK community_posts (ON DELETE CASCADE) | MVP soft-hide라 CASCADE 휴면 |
| user_id | BIGINT FK users (ON DELETE CASCADE) | |
| (BaseEntity) | created_at 등 | 추천순 윈도우 집계 기준 |

UNIQUE `uk_community_post_likes_post_user(post_id, user_id)`, 인덱스 `idx_community_post_likes_post_created(post_id, created_at)`.

### 4.38 academy_videos ✅ 비동기 학원 홍보 영상 (V930 → V936~V938)

승인된 raw를 즉시 공개하고, durable DB job이 나중에 H.264/AAC MP4와 poster를 만든다.
공개 가능 lifecycle과 최적화 상태는 분리한다. 모든 소유권 검사는 별도 owner 컬럼 없이
`academy_videos.academy_id → academies.tutor_user_id`로만 파생한다.

| column | type | note |
|---|---|---|
| video_id | BIGINT PK | |
| academy_id | BIGINT FK academies (RESTRICT) | 영상·세션·cleanup이 남아 있는 Academy 물리 삭제 차단 |
| original_filename | VARCHAR(255) | 표시용 원본명 |
| lifecycle_status | VARCHAR(20) NOT NULL | `READY` / `DELETING` / migration-only `UNAVAILABLE` |
| active_source | VARCHAR(20) NOT NULL | `RAW` / `NORMALIZED` |
| raw_storage_key / raw_media_type / raw_size_bytes | | 승인 raw의 불투명 key, canonical media type, 양수 크기 |
| normalized_storage_key / normalized_media_type / normalized_size_bytes | | 성공 산출물. media type은 `video/mp4` |
| thumbnail_key | VARCHAR(500) NULL | RAW 공개 중에는 nullable, NORMALIZED에는 필수 |
| duration_ms | BIGINT | 밀리초 정밀도. `<=120000` 허용 |
| width / height | INT | 검증된 source metadata |
| source_version | BIGINT NOT NULL | lifecycle/source 전이 CAS용 명시 버전(`>=0`), JPA `@Version` 아님 |
| raw_cleanup_status | VARCHAR(20) NOT NULL | `NOT_REQUIRED` / `PENDING` / `DONE` |
| created_at / updated_at / created_by / updated_by | | 감사 |

**source 불변식:** 공개 `RAW`는 READY이며 raw key/type/양수 size와 `NOT_REQUIRED`가 필수다.
삭제 tombstone인 `DELETING + RAW`는 같은 raw metadata와 `PENDING` cleanup을 유지한다.
`NORMALIZED`는 mp4 key/type/양수 size/thumbnail이 필수다. source-switch 직후에는 raw metadata와
`PENDING` cleanup을 함께 유지하고, 저장소 삭제가 확인된 뒤에만 raw 필드를 비우고 `DONE`으로 바꾼다.
`source_version`은 active source 또는 lifecycle 전이마다 증가한다.

**legacy 보존:** V930 `READY`는 `READY + NORMALIZED + SUCCEEDED + DONE`,
V930 `FAILED`는 공개하지 않는 `UNAVAILABLE + NORMALIZED + FAILED + DONE`으로 이관한다.
인덱스는 공개 목록용 `idx_academy_videos_academy_lifecycle_created(academy_id, lifecycle_status, created_at DESC)`와 bounded cleanup reconciliation용 `idx_academy_videos_cleanup_reconcile(lifecycle_status, active_source, raw_cleanup_status, video_id)`를 둔다.

**durable raw 제한값** (`app.video.*`, 시작 시 정확한 product invariant로 검증):
raw `<=209715200` bytes, duration `<=120000ms`, 한 Academy의 non-DELETING 영상(직접 업로드 + 외부 링크) +
만료되지 않은 `INITIATED|COMPLETING` 예약 합계 최대 30개, workflow deadline 180000ms.
정규화 산출물 상한은 기존 100MiB를 유지한다. 클라이언트 사전검증은 UX용일 뿐이고
저장소 Head/stream counter/ffprobe와 DB `CURRENT_TIMESTAMP(6)`가 최종 판정한다.

#### academy_video_upload_sessions (V936)

서버가 정한 staging key/multipart upload ID를 가진 180초 업로드 예약이다. 상태는
`INITIATED / COMPLETING / ACCEPTED / REJECTED / ABORTED / EXPIRED`.
`session_id`, `academy_id`, 요청 파일명/선언 크기, part 크기/예상 개수, `deadline_at` 외에
완료 saga의 `finalization_token`, immutable `part_manifest_json`/`manifest_hash`,
`next_finalize_attempt_at`, recovery owner/lease, 시작/종료 시각, completed-object ETag/크기,
UNIQUE `accepted_video_id`, 안전한 오류 코드를 보존한다. COMPLETING만 token+manifest를 가지며,
terminal 상태가 token을 무효화한다. recovery lease는 COMPLETING에서 owner/expiry가 함께 있을 때만
유효하다. `(academy_id,state,deadline_at)`는 live quota reservation 계산에 사용한다.

#### academy_video_optimization_jobs (V936)

`video_id` UNIQUE로 영상당 job은 최대 하나다. 상태는
`PENDING / PROCESSING / RETRY_WAIT / SUCCEEDED / FAILED / CANCELLED`, `attempt_count`는 `0..2`.
PROCESSING은 attempt 1 또는 2와 lease owner/token/expiry/heartbeat를 모두 요구하며 다른 상태는
lease 필드를 모두 비운다. `next_attempt_at`, progress/start/finish 시각, 안전한 error code와
500자 diagnostic summary를 보존한다.

#### academy_video_asset_cleanups (V936)

storage 삭제를 best-effort 메모리 보상으로 끝내지 않는 durable outbox다.
`dedupe_key` UNIQUE, 선택적인 session/video/job FK, asset class/key/reason, 상태
`PENDING / PROCESSING / DONE`, attempts/next-attempt 및 lease/error 필드를 가진다.
source switch는 raw cleanup row와 `PENDING`을 같은 트랜잭션에 기록하고, 삭제 tombstone은 알려진
모든 asset cleanup을 같은 트랜잭션에 MySQL `INSERT IGNORE` 기반 atomic dedupe insert한다(read-then-insert 금지).
일반 object는 delete 뒤 Head에서 부재를 확인하고, multipart는 abort 성공 또는 확정 `NoSuchUpload`/404만 확인으로 인정한 뒤 DONE 처리한다. unknown/transient abort는 retry한다. raw field는 matching cleanup DONE과 같은 트랜잭션에서만 비운다.
모든 cleanup 완료 전 video/session/job 물리 삭제와 부모 Academy 삭제를 허용하지 않는다. DB reconciliation은
`app.video.cleanup-reconciliation-batch-size`로 제한된 indexed first-page query만 수행하고 저장소 orphan list도 같은 상한을 쓴다. cleanup poll/reconcile scheduler와 executor는 optimization heartbeat/recovery와 분리한다.

**노출/삭제 의미:** READY+RAW는 최적화가 PENDING/PROCESSING/RETRY_WAIT/FAILED여도 공개 가능하다.
DELETING/UNAVAILABLE은 강사·공개 목록과 새 source resolution에서 제외한다. 삭제는 복구 가능한
soft delete가 아니라 cleanup 완료 뒤 물리 삭제되는 내부 tombstone이다.

**운영 경계:** S3 lifecycle은 incomplete multipart 최후 방어만 담당하며 active raw/normalized/attempt
자산을 시간 기반으로 삭제하지 않는다. instance profile, CORS `ETag`, ALB, temp disk, ffmpeg 및
관찰성 계약은 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md)가 단일 출처다.

### 4.39 landing_video_slots ✅ 랜딩 고정 영상 슬롯 (V933)

랜딩 페이지(`/`)의 서로 다른 의미를 가진 영상 3개를 빌드 없이 교체·숨김·복원하기 위한 현재 상태 테이블이다.
슬롯은 `HERO_BACKGROUND`, `ACADEMY_SHORTS_DEMO`, `PARENT_REPORT_DEMO`로 고정하며 임의 생성·삭제·정렬은 하지 않는다.
V933이 세 row를 공개 상태이면서 managed asset이 없는 상태로 초기화한다.

| column | type | note |
|---|---|---|
| slot | VARCHAR(40) PK | 고정 enum 식별자 |
| original_filename | VARCHAR(255) NULL | 관리자 표시용 업로드 원본 파일명 |
| storage_key | VARCHAR(500) NULL | 변환된 mp4의 불투명 `FileStorage` key |
| thumbnail_key | VARCHAR(500) NULL | poster jpg의 불투명 `FileStorage` key |
| duration_sec | INT NULL | 변환 후 길이 |
| size_bytes | BIGINT NULL | 변환된 mp4의 실제 바이트 크기 |
| width / height | INT NULL | 변환 후 해상도 |
| is_visible | BOOLEAN NOT NULL DEFAULT TRUE | 공개 랜딩 노출 여부. asset 교체와 독립적으로 유지 |
| created_at / updated_at | DATETIME(6) NOT NULL | `BaseEntity` 감사 시각 |
| created_by / updated_by | VARCHAR(50) NULL | `BaseEntity` 감사 사용자 |

**상태 해석:**
- row가 없거나 `is_visible=true`이면서 `storage_key`가 없으면 기존 번들 mp4를 사용한다.
- `is_visible=false`이면 asset 유무와 무관하게 공개 랜딩에 media element를 렌더하지 않는다.
- `is_visible=true`이면서 managed asset이 있으면 공개 proxy stream을 사용한다.
- 업로드 교체는 기존 `is_visible` 값을 보존한다. 물리 삭제 UI는 제공하지 않고 교체 성공 뒤 이전 asset만 best-effort로 정리한다.

**저장소/전송 제한:** 랜딩 영상은 `FileStorage.readSingleLimited`로만 읽는다. 기본값은 업로드 100MB,
변환 산출물 30MB, 썸네일 2MB, 동시 전송 3개이며 Local/S3 구현 모두 metadata와 실제 stream을 확인하고
최대 `상한 + 1 byte`만 할당해 초과 여부를 판정한다. S3 SDK 조회 실패는 key/cause 없는 checked storage
exception으로 정규화한다. API와 로그에는 storage key나 로컬 절대경로를 노출하지 않는다.

### 4.40 beta_comments ✅ 랜딩 베타 참여자 의견 (V939)

랜딩(`/`)의 "베타 서비스에 한마디 남겨주세요" 폼으로 로그인 사용자가 남긴 의견과, 어드민 승인을 통과한
것만 같은 페이지 "베타 참여자들의 목소리" 캐러셀에 노출하기 위한 모더레이션 테이블이다.

| column | type | note |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT NOT NULL | FK → `users(user_id)` ON DELETE CASCADE |
| nickname | VARCHAR(12) NOT NULL | 작성 시점 별명 스냅샷 |
| participant_type | VARCHAR(20) NOT NULL | `BetaParticipantType` — 원장/강사/공부방/교습소/기타 |
| comment | VARCHAR(80) NOT NULL | 의견 본문 |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | `PENDING` / `APPROVED` / `HIDDEN`. **등록은 `APPROVED`로 시작한다** — 컬럼 DEFAULT는 쓰이지 않는다(JPA가 항상 명시). §등록 정책 참고 |
| highlight_label | VARCHAR(20) NULL | `NEW` / `REVIEWING` / `APPLYING` / `APPLIED`. NULL이면 뱃지 미노출 |
| created_at / updated_at | DATETIME(6) | `BaseEntity` 감사 시각 |
| created_by / updated_by | VARCHAR(50) NULL | `BaseEntity` 감사 사용자 |

인덱스: `idx_beta_comments_status_created(status, created_at)` — 랜딩의 "APPROVED 최신순 10건" 조회와
어드민 상태별 목록을 함께 태운다.

**표시모드 테이블이 없는 이유:** 초기 설계에는 `DUMMY`/`REAL`을 어드민이 수동 전환하는
`beta_landing_setting` 단일행 테이블이 있었으나 채택하지 않았다. 설정값과 실제 승인 데이터가 어긋난
상태(REAL인데 승인 0건 등)를 만들지 않기 위해 **전환을 승인 건수에서 파생**시킨다.

**캐러셀 렌더 분기** (`BetaCommentService.renderCarousel()`):

| 승인 건수 | 렌더 | 애니메이션 |
|---|---|---|
| 0건 | 자리표시 더미 5장 (`BetaCommentService.DUMMY_CARDS`) | 있음 |
| 1~4건 | 실데이터 N장 | **없음. 정적 가운데 정렬** |
| 5건 이상 | 실데이터 최신순 최대 10장 | 있음 |

- 승인이 1건이라도 생기면 더미는 사라지며 더미와 실데이터를 **섞지 않는다.**
- ⚠ `landing.css`의 `@keyframes feedback-scroll`이 `translateX(calc(-50% - 8px))`이라 트랙은 **정확히
  동일한 두 절반**이어야 한다. 애니메이션을 켤 때 원본 N장 + `aria-hidden` 복제본 N장을 만드는 책임은
  `BetaCommentCarousel.of()`에 있고 경계값(0/1/4/5/10)은 `BetaCommentServiceTest`가 고정한다.
- ⚠ 1~4건에서 애니메이션을 끄는 이유는 `.feedback-card`가 고정 310px이라 4장(1240px + gap)이어도 넓은
  화면에서 트랙이 `.feedback-window`보다 좁아져 빈 구간을 스크롤하기 때문이다. 이 구간은
  `.feedback-window.is-static`이 자동 스크롤과 좌우 페이드를 함께 끈다.

**등록 정책:** 로그인 필수(`/api/**` catch-all이 `.authenticated()`), **횟수 제한 없음**.
`user_id` unique 제약을 두지 않으므로 1인 다건이 허용된다. 등록은 `BetaComment.INITIAL_STATUS`(현재
`APPROVED`)로 시작해 **사전 승인 없이 바로 노출**되며, 어드민은 사후에 숨기는 역할이다. 즉 지금은
노출을 막는 사전 게이트가 없다. 스팸이나 부적절한 의견이 문제가 되면 그 상수를 `PENDING`으로
되돌리는 것만으로 승인 대기 흐름이 되살아난다 — 어드민의 대기 큐와 승인 액션은 그대로 유지돼 있다. 길이·enum 검증은 클라이언트 `maxlength`가 아니라 `BetaComment` 팩토리가
단일 지점에서 판정한다. 폼 안내의 "개인정보나 학생 이름은 적지 마세요"는 안내일 뿐 강제 수단이 없다.

**상태 전이:** `APPROVED ↔ HIDDEN`이 현재의 주 경로이고, 사전 모더레이션을 켜면 `PENDING → APPROVED`가
추가된다. **`PENDING`으로는 되돌릴 수
없다**(`BetaComment.updateStatus`가 거절) — 이미 검토한 의견이 미검토 큐로 섞여 들어가는 것을 막는다.
`nickname`은 `users.nickname`과 별개인 스냅샷이라 회원정보 변경에 연동하지 않는다 — 어드민이 승인 시점에
본 문구가 그대로 노출되어야 한다.

### 4.41 academy_daily_visits ✅ 학원 홈페이지 일별 방문 집계 (V941)

공개 Academy 홈페이지의 `오늘`, `누적`, 최근 7일 방문 횟수를 즉시 표시하기 위한 1st-party 집계다.
GA4는 유입 채널·행동 분석용으로 유지하고, 공개 화면은 외부 API 지연·쿼터·자격증명에 의존하지 않는다.

| column | type | note |
|---|---|---|
| daily_visit_id | BIGINT PK AUTO_INCREMENT | |
| academy_id | BIGINT NOT NULL | FK → `academies(academy_id)` ON DELETE CASCADE |
| visit_date | DATE NOT NULL | `Asia/Seoul` 기준 집계일 |
| visit_count | BIGINT NOT NULL DEFAULT 0 | 해당 날짜의 세션 기준 방문 횟수 |
| created_at / updated_at | DATETIME(6) | `BaseEntity` 감사 시각 |
| created_by / updated_by | VARCHAR(50) NULL | `BaseEntity` 감사 사용자 |

`UNIQUE(academy_id, visit_date)`로 학원·날짜당 한 row만 두고 MySQL upsert로 원자 증가시킨다. 원시 IP,
User-Agent, 브라우저 식별자는 저장하지 않는다. 같은 HTTP 세션의 같은 학원·같은 날짜 요청은 한 번만
증가하므로 새로고침은 중복 집계하지 않는다. 누적값은 일별 `visit_count` 합이며 사람의 영구 고유 수가 아니다.

### 4.42 buildit_chart ✅ BUILD IT CHART Google Discovery V2 (V947)

BUILD IT 홈페이지의 공개 `/chart`와 ADMIN 전용 `/admin/chart`를 위한 지역 영어교육자 발굴/승인/공개 차트 스키마다.
복구본의 Google Educator Discovery V2 흐름을 Spring/JPA로 이식했으며, 1차 실행 권역은 `apgujeong-sinsa`(압구정·신사)만 허용한다.
AI 결과는 곧바로 공개 차트에 들어가지 않고 **후보 → 관리자 승인 → Educator DB → 공개 Ranking** 순서를 따른다.

#### `chart_discovery_settings`

관리자가 `/admin/chart`에서 바꾸는 검색 설정 singleton(`id='default'`).

| column | type | note |
|---|---|---|
| id | VARCHAR(40) PK | 현재 `default` 1행 |
| search_zone | VARCHAR(60) | 기본 `apgujeong-sinsa`; 실행은 압구정·신사만 허용 |
| include_activities | TEXT | 수업/내신/시험분석/교육칼럼/YouTube/Shorts 등 포함 활동 |
| excluded_targets | TEXT | 대형학원/프랜차이즈/인강 중심 강사 등 제외 지시 |
| candidate_limit | INT | 1~10명으로 clamp |
| deep_search_enabled | BIT | 후보별 Deep Search 사용 여부 |
| extra_instruction | TEXT | 추가 검색 지시 |
| created_at / updated_at / created_by / updated_by | audit | `BaseEntity` |

#### `chart_candidates`

Google V2가 발견한 관리자 검토 후보. 후보는 `DISCOVERED`로 저장되고, 관리자 액션으로만 `APPROVED` 또는 `REJECTED`가 된다.

| column | type | note |
|---|---|---|
| candidate_id | BIGINT PK AUTO_INCREMENT | |
| zone_id | VARCHAR(60) | 검색권역 ID |
| person_key | VARCHAR(180) | 정규화 `이름|활동지역` |
| name / role / academy_name / activity_region | VARCHAR | 개인 교육자 후보 정보 |
| content_type / content_summary | VARCHAR/TEXT | 처음 발견된 교육활동 콘텐츠 |
| source_url | VARCHAR(1000) | 원문 링크. 프론트는 [원문 확인]으로만 사용 |
| source_url_hash | VARCHAR(64) | URL SHA-256. 긴 URL unique index 용도 |
| source_title | VARCHAR(500) NULL | Grounding title이 있으면 저장 |
| discovery_reason | TEXT | CONTENT → PERSON 후보 판단 근거 |
| deep_search_result | TEXT | 후보별 Deep Search 요약 JSON/문구 |
| exclusion_check | TEXT | VERIFIED/REVIEW_REQUIRED/제외 판단 이유 |
| verification_status | VARCHAR(30) | `VERIFIED` / `REVIEW_REQUIRED` |
| status | VARCHAR(30) | `DISCOVERED` / `APPROVED` / `REJECTED` |
| google_query | VARCHAR(500) | 실행 prompt 앞부분. 키/비밀값 없음 |
| discovered_at | DATETIME(6) | 발견 시각 |
| audit columns | | `BaseEntity` |

인덱스:
- `uk_chart_candidate_zone_person_source(zone_id, person_key, source_url_hash)` — 같은 사람/같은 원문 중복 저장 방지
- `idx_chart_candidates_zone_status(zone_id, status)`

#### `chart_candidate_activities`

후보별 원문 근거 목록. CONTENT 발견 근거와 Deep Search에서 수집한 복수 활동 근거를 저장한다.

| column | type | note |
|---|---|---|
| activity_id | BIGINT PK AUTO_INCREMENT | |
| candidate_id | BIGINT FK | `chart_candidates(candidate_id)` |
| content_type | VARCHAR(80) | 글/칼럼/수업/YouTube/Shorts 등 |
| content_summary | TEXT | 활동 요약 |
| source_url | VARCHAR(1000) | 원문 링크 |
| discovered_at | DATETIME(6) | 발견 시각 |

`UNIQUE(candidate_id, source_url(255))`로 같은 후보의 동일 링크 중복을 막는다.

#### `chart_educators`

관리자가 승인한 교육자 DB. 공개 `/chart`는 이 테이블만 읽는다.

| column | type | note |
|---|---|---|
| educator_id | BIGINT PK AUTO_INCREMENT | |
| person_key | VARCHAR(180) UNIQUE | 같은 사람 재승인 시 갱신 |
| name / role / academy_name / activity_region | VARCHAR | 공개 Chart 정보 |
| zone_id | VARCHAR(60) | 현재 공개 권역 |
| approved_from_candidate_id | BIGINT FK | 승인 원본 후보 |
| approved_at | DATETIME(6) | 최초 승인 시각 |
| audit columns | | `BaseEntity` |

Ranking 1차 구현은 `approved_at DESC` 기준으로 최대 100명을 공개한다. AI 자동 등록 경로는 없다.

### 4.43 buildit_chart 전국 Living Archive 조사 (V949 초안 → V951/V952 전국 전환, PR-A)

AI가 전국의 원장·강사를 공개 웹에서 조사해 교육철학·커리큘럼을 근거와 함께 축적하는 Living Archive다. 초기 전국 구축 단계에서는 관리자 사전 승인 없이 정상 조사 파이프라인 완료 시 서버가 자동 발행한다(별도 AI/WebMCP publish tool 없음).

- `chart_regions`: 대한민국 전국 시·군·구 조사 registry(V952 일괄 seed, region_id = `<시도>-<시군구>` roman). `UNRESEARCHED`, `IN_PROGRESS`, `RESEARCHED`, `DUE`, `ERROR`. 운영자 확정 조사 완료 = 서울특별시 25개 구 전체 + 부산 해운대구 + 분당구 = 27개 → `RESEARCHED`(선정 0명인 구도 완료). `RESEARCHED` → `next_research_at`(기본 `chart.re-research-days`=90일) 뒤 `DUE`. `get_next_regions`는 신규/재조사 대상만 반환하며 지역 추가는 migration seed로만 한다.
- `chart_candidates` 추가 nullable 필드(V951): `education_philosophy_summary`, `curriculum_summary`, `distinctive_teaching_summary`, `recent_activity_summary`, `strengths_json`, `improvement_suggestions_json`, `profile_image_url`, `profile_image_source_url`, `legacy_source`, `legacy_metadata_json`, `excluded_from_chart`/`excluded_reason`/`excluded_at`. 조사 1회로 저장한 이 값이 그대로 상세 프로필에 쓰인다(같은 교육자 재조사 없음). V949의 `education_philosophy`/`curriculum_features` 등 구 필드는 그대로 두되 신규 저장은 `*_summary` 컬럼을 사용한다.
- `chart_candidate_activities` 추가 nullable 필드: 활동일, evidence 종류, 교육적 정보성, 조사일. 전화·이메일·개인주소는 저장하지 않는다.
- `chart_score_snapshots`: 서버 결정론 Score breakdown 과 실제 근거(`evidence_json` = 활동·공개 채널·철학/커리큘럼 근거), 기준 버전, 계산일, 검토 상태(`AUTO_PUBLISHED`/`AUDIT_RECORDED`/…)를 저장한다. AI는 점수를 입력하지 않는다.
- `chart_review_batches`: 초기 자동발행 단계에서 관리자 사전 승인 gate가 아니라 "누가·어떤 근거로·어떤 Score로 선정했는지" audit record. status 값 `AUTO_PUBLISHED`/`AUDIT_RECORDED`/`RESEARCHED_ZERO`(0명 조사 완료)/`REVERTED`(사후 되돌림) 추가(VARCHAR, 스키마 변경 없음). 기존 `PENDING_ADMIN_REVIEW` 행은 그대로 읽힌다.
- **조사 파이프라인 = 정보 아카이브 우선(V954, PR-A)**: `eligibility_status` 는 이제 `EVALUATABLE`(점수·TOP100 대상) / `RESEARCH_PENDING`(저장·공개 검색 노출, 점수 제외, 향후 Evidence 보강 대상 — 폐기 아님) 두 값을 쓴다(VARCHAR, 스키마 변경 없음). 기존 `'ELIGIBLE'` 50건은 V954에서 `EVALUATABLE` 로 승계. `save_research_drafts` 는 **최소 게이트**(① 교육자 개인 식별 ② 현재 초·중·고 현장 영어교육 범위를 명백히 벗어나지 않음 = `exclusionFree` ③ 공개 근거 URL) 만 통과하면 저장하고, 실패 시에만 `REJECTED`/422(행 없음). **평가 게이트**(현재 오프라인 활동·최근 연도 근거·교육철학·커리큘럼·비광고 교육 근거) 실패는 `RESEARCH_PENDING` 으로 저장. `previewScore`/`writeScoreSnapshot`/`completeRegion` 선정은 `EVALUATABLE`(또는 Legacy grandfather)만 허용. **학원 규모·프랜차이즈 소속·광고량·유명세는 제외·감점 사유가 아니다**(`score()` 산식 불변). "현재 오프라인 활동 미확인"(단순 미확인 → 보류)과 "명백히 현장 교육자 아님"(`exclusionFree=false` → 거부)을 구분한다. 범위: 대한민국 초·중·고 영어교육만.
- **선정 범위 규칙**: `complete_region` 은 `selectedCandidateIds` 로 넘어온 후보만 이 지역 선정자로 인정한다(각 후보는 이 지역 `zone_id` 소속·`EVALUATABLE`·미제외·서술가능이어야 함). `save_research_drafts` 저장은 후보 풀 등록일 뿐 선정이 아니며 서버가 `zone_id` 로 재유도하지 않는다. `autoPublishNationwide` 는 "직전 PUBLISHED snapshot 교육자 ∪ 이번 명시 선정자"(제외자 제거) 기준으로 새 snapshot 을 만든다 — Score snapshot 이 있는 모든 후보를 재유입시키지 않는다.
- `chart_publications` / `chart_publication_entries`(V951 완화): `week_start` UNIQUE 제거(같은 날/주 여러 지역 발행 가능), `review_batch_id` NULL 허용, `scope`('NATIONWIDE') 추가. publication = 그 시점 전국 유효 교육자 전체 snapshot이며 현행 Score로 재정렬한다. entry_type `RANKED`(최대 100) / `DISCOVERED`(순위 밖) / `PROMISING`(순위 밖 + potential signals). `chart_rank` NULL 허용. 새 발행 시 직전 PUBLISHED는 `SUPERSEDED`로 보존(삭제 없음). current publication = 가장 최근 `PUBLISHED` 1건(`published_at DESC, publication_id DESC`).
- `chart_web_draft_requests`: 교육자 동의 시각과 Chart 프로필 snapshot payload를 기록하는 Chart → BuildIt Web 계약이다. 자동 홈페이지 생성이나 Score 가산은 하지 않는다.
- **Legacy V3 승계(V953, PR-B)**: 최초 BuildIt Chart 선정 43명(서울 auto 37 + 강남/부산 수동 6)을 `chart_candidates`에 `legacy_source IN ('V3_AUTO','V3_MANUAL')`로 import. 전화·이메일·상세주소 제외(스캔·치환), Legacy placeholder Score(2.0) 미저장 — 현행 Score는 서버가 재계산. 확보된 정보(`content_summary`·`discovery_reason`·근거 URL 1개)까지만 저장하고 없는 교육철학·커리큘럼은 비운다(생성 금지). `legacy_metadata_json`에 `v3Score`(`v3ScoreIsCurrent:false`) 등 이력 보존. 현행 강화 Hard Gate는 `legacy_source` 보유 시 grandfather.
- **최초 publication 부트스트랩**: `ChartInitialPublicationRunner`가 앱 시작 시 `PUBLISHED` publication 0건 + 공개 가능한 선정 교육자 존재 시 1회 `rebuildNationwidePublication` 호출(멱등). 이후에는 `complete_region`이 갱신. 관리자 수동 재생성은 `POST /api/admin/chart/tools/publications/rebuild`.
- **잘못 발행 batch 사후 복구**: `inspectReviewBatch(id)`(읽기 전용, WebMCP `inspect_chart_review_batch`)로 batch 의 candidate·zoneId·activityRegion·snapshot·entry 확인 후 `revertReviewBatch(id)`(WebMCP `revert_chart_review_batch`, `ROLE_ADMIN`) 실행. 해당 batch 의 `chart_publication_entries`·`chart_score_snapshots` 만 삭제하고 batch → `REVERTED`, 이어서 전국 publication 을 직전 정상 명단으로 재생성한다(별도 rebuild 불필요). entry 가 빠진 candidate 중 **다른 정상 batch snapshot(`review_batch_id IS NOT NULL`)이 남아 PUBLISHABLE 한 사람은 재게시**되고, 이 batch 로만 유입된 사람만 빠진다. `chart_candidates`·activity·Legacy·Score 산식·`chart_regions` 상태는 불변. 이미 `REVERTED` 면 무변경(idempotent).
- **기존 교육자 Educator Intelligence 보완(PR-D, `ChartCandidateEnrichmentService`)**: `POST /api/admin/chart/candidates/{candidateId}/enrichment` (스키마 변경 없음). 신규 발굴 저장(`saveDraft` = INSERT)과 분리된 **기존 `candidate_id` 전용 UPDATE**로, Legacy import 처럼 요약이 빈 교육자를 이후 재조사해 채워 넣는 경로. 수정 범위는 `education_philosophy_summary`·`curriculum_summary`·`distinctive_teaching_summary`·`recent_activity_summary`·`strengths_json`·`improvement_suggestions_json` 과 `chart_candidate_activities` Evidence 추가뿐. 병합: 요약은 빈 필드만 채우고 값이 있으면 `overwrite:true` 일 때만 교체(null/blank 로 삭제 불가), 리스트는 합집합 누적, Evidence 는 원 출처 URL 만 누적(네이버 AI/검색·grounding redirect·placeholder 거부, `(candidate_id, source_url)` 중복 무시, 기존 Evidence 삭제 안 함). **Score·Ranking·`eligibility_status`·publication·`excluded_from_chart`·`legacy_source`·identity 는 불변**(요청 DTO 에 해당 필드가 없음). 재평가가 아니므로 `saveDraft` 의 교육철학·커리큘럼 평가 게이트를 적용하지 않는다.

파이프라인 순서: 정보 수집 → 정보 구조화(저장) → 검색/발견 → **평가 가능 여부 판단** → 점수화 → TOP100. 저장 **최소 게이트**(개인 식별 · 현장 영어교육 범위 · 공개 근거 URL) 실패만 `REJECTED`/422. **평가 게이트**(현재 오프라인 활동 · 최근 연도 근거 · **교육철학** 2~3문장(광고 문구 불가) · **커리큘럼/고유 수업 구조** 2~3문장(과목 나열 불가) · 광고 아닌 실제 교육 정보 근거) 실패는 `RESEARCH_PENDING` 으로 저장하며 낮은 Score 를 주지 않는다 — 점수·TOP100 대상은 `EVALUATABLE` 뿐. 지역별 선정 인원 목표는 없다(0명도 정상). Legacy V3 Score(대부분 placeholder 2.0)는 현행 Score로 사용하지 않으며 `legacy_source` 보유자는 평가 게이트를 grandfather 로 통과한다. 공개 검색 API(`RESEARCH_PENDING` 포함)와 UI 는 PR-B/PR-C.

BuildIt Score `buildit-chart-v1`은 최대 100점이다: 교육자 고유성 35, 수업·교육 실천 20, 교육 콘텐츠 지속성 15, 전문성 15, 검증 근거 10, 지역 활동 5. 검색량·광고량·규모·유명세·BuildIt 가입 여부는 입력하지 않는다.

Legacy V3는 현행 도메인 테이블이 아니다. read-only exporter가 VERIFIED 43명을 `PENDING_CURRENT_RESEARCH` 전송 형식으로 만들 뿐이며 `chart_candidates`, `chart_educators`, Score snapshot 또는 publication에 직접 넣지 않는다. 전화·이메일·상세주소는 전송 형식에 없고, Legacy Score는 `legacy_score_is_current_score=false`인 역사 메타데이터로만 보존한다.

### 4.44 wiki_exam_originals ✅ 공개 BuildIt Wiki 모의고사 원본 PDF (V956)

공개 `/wiki` 의 모의고사 원본(문제지/정답지) PDF 저장소. `exam_analyses`(HTML 분석 리포트, sandbox iframe 렌더)와 목적이 달라 별도 테이블. 파일은 `FileStorage.saveRaw` 로 원본 그대로 저장하고 `/api/wiki/originals/{id}/download` 로 첨부 다운로드만 제공한다. CRUD는 ADMIN, 공개 조회는 grade(1~3) 필터.

| column | type | note |
|---|---|---|
| wiki_exam_original_id | BIGINT PK AUTO_INCREMENT | |
| title | VARCHAR(200) NOT NULL | |
| grade | INT NOT NULL | 1\|2\|3 (고1/고2/고3) |
| exam_label | VARCHAR(100) NOT NULL | 예: "2026 9월 모의고사" |
| doc_type | VARCHAR(20) NOT NULL | enum name(): `QUESTION`(문제지) / `ANSWER`(정답지) |
| original_file_name | VARCHAR(255) NOT NULL | 업로드 원본 파일명(다운로드 시 사용) |
| storage_key | VARCHAR(500) NOT NULL | FileStorage 불투명 키 |
| file_size | BIGINT NOT NULL | |
| is_visible | BIT(1) NOT NULL | 공개 여부 |
| is_deleted | BIT(1) NOT NULL DEFAULT b'0' | soft delete(삭제 시 visible=false 동반) |
| (BaseEntity) | created_at/updated_at/created_by/updated_by | |

인덱스: `idx_wiki_exam_originals_public(is_deleted, is_visible, grade, created_at, wiki_exam_original_id)`.

### 4.45 wiki_analysis_materials ✅ 공개 BuildIt Wiki 분석 자료 문서 (V957)

공개 `/wiki` 의 분석 자료 **문서 파일**(PDF/HWP/HWPX/DOC/DOCX/XLS/XLSX/PPT/PPTX/ZIP). `exam_analyses`(/analyzes, HTML 콘텐츠)와 완전히 분리된 별도 시스템(`domain/wikianalysis/*`). `FileStorage.saveRaw` 원본 저장, `/api/wiki/analysis-materials/{id}/download` attachment 다운로드. CRUD는 ADMIN.

| column | type | note |
|---|---|---|
| wiki_analysis_material_id | BIGINT PK AUTO_INCREMENT | |
| title | VARCHAR(200) NOT NULL | |
| description | VARCHAR(1000) NULL | 선택 |
| school_name | VARCHAR(100) NULL | 자유 입력(전국이면 비움) |
| grade | INT NOT NULL | 1\|2\|3 |
| exam_year | INT NOT NULL | (엔티티 필드명 `year`; H2 예약어 회피로 컬럼은 `exam_year`) |
| semester | VARCHAR(20) NOT NULL | 자유 입력 |
| exam_type | VARCHAR(40) NOT NULL | |
| subject | VARCHAR(60) NULL | blank→서비스에서 "영어" |
| content_type | VARCHAR(100) NOT NULL | 확장자로 추론(`DocumentUploadValidator.contentType`) |
| original_file_name / storage_key / file_size | VARCHAR(255) / VARCHAR(500) / BIGINT | |
| is_visible / is_deleted | BIT(1) NOT NULL / BIT(1) NOT NULL DEFAULT b'0' | soft delete |
| (BaseEntity) | created_at/updated_at/created_by/updated_by | |

인덱스: `idx_wiki_analysis_materials_public(is_deleted, is_visible, grade, created_at, wiki_analysis_material_id)`.

### 4.46 community_post_attachments ✅ 공개 커뮤니티 게시글 문서 첨부 (V958)

open-community 게시글의 원본 문서·이미지 첨부. BuildIt Wiki 변형문제·자료 공유·요청(`VARIANT`/`MATERIAL`) 게시판에서만 사용(`EXAM_REVIEW`는 텍스트 전용). `lesson_community_attachments`와 형태는 같지만 별개 테이블이다. 본문 이미지(`community_images`, 인라인·재인코딩)와도 별개 — 원본 그대로 보관.

허용 형식: 문서(`pdf/hwp/hwpx/doc/docx/xls/xlsx/ppt/pptx/zip`) + 이미지(`jpg/jpeg/png/webp`). `DocumentUploadValidator.validateAttachment` — 확장자 화이트리스트 + 매직바이트 교차검증(이미지는 JPEG `FF D8 FF` / PNG 8B 시그니처 / WebP `RIFF…WEBP`) + 실행파일(MZ/ELF/`#!`) 위장 차단. 글+첨부는 `POST /api/open-community/posts`(multipart)로 **한 트랜잭션에서 원자적 생성** — 첨부 실패 시 글도 롤백(고아 글 방지).

| column | type | note |
|---|---|---|
| attachment_id | BIGINT PK AUTO_INCREMENT | |
| post_id | BIGINT NOT NULL FK community_posts(post_id) ON DELETE CASCADE | |
| uploader_user_id | BIGINT NOT NULL FK users(user_id) | |
| original_file_name / content_type / file_size / storage_key | VARCHAR(255) / VARCHAR(100) / BIGINT / VARCHAR(500) | |
| is_visible | BIT(1) NOT NULL DEFAULT b'1' | 글 숨김 시 목록·serve에서 제외(스토리지는 명시적 삭제 시에만 정리) |
| (audit) | created_at/updated_at/created_by/updated_by | |

인덱스: `idx_cpa_post(post_id, is_visible, attachment_id)`. 게시글당 최대 10개, 파일당 20MB, `DocumentUploadValidator` 통과 필수.

### 4.47 question_wikis + THREAD ✅ 공개 BuildIt Wiki 문항별 Question Wiki (V959)

한 모의고사 문항을 장기적으로 진화하는 Knowledge Unit 으로 취급한다. 모든 하위 데이터는 안정적 `question_wiki_id` 로 연결되어, 향후 AI 가 문항 전체 Context 를 한 번에(`GET /api/wiki/questions/{id}/context`) 읽는다. 기존 `/wiki` 커뮤니티(`community_posts` scope=wiki)·자료실(`wiki_exam_originals`/`wiki_analysis_materials`)은 건드리지 않는다.

- **`question_wikis`** — 문항. `UNIQUE(subject, grade, exam_year, exam_label, question_number)`. `passage_group_key` 로 공동지문(41-42 등) 묶음. seed: 2026년 9월 전국연합학력평가 고1·고2 영어, 문항 20~24 / 29~42(38행).
- **`question_wiki_material_links`** — 문항 ↔ `wiki_exam_originals`/`wiki_analysis_materials` 링크(참조만). ADMIN 관리.
- **`question_thread_posts`** — THREAD 글(+ `parent_post_id` 1단계 답글). 두 축을 저장: `category`(질문/해석·구문/어법/어휘/출제포인트/예상문제/변형문제/풀이·해설/해설개선/수업인사이트/기타) + `data_kind`(`DISCUSSION` 커뮤니티 지식 / `GENERATED_PRACTICE` 사용자 제작 문제 / `EXAM_EVIDENCE` 실제 기출 증거). `author_role` 은 작성 시점 스냅샷(공개 participant type = 교육자/학생/운영자). soft-hide(`is_visible`+`hidden_by`). **단순화된 UI 는 분류를 사용자에게 요구하지 않아 `category` 미지정 글은 `ETC` 로 저장되고, 두 필드 모두 향후 Browser AI 자동분류가 갱신한다(스키마·enum 은 그대로 유지).**
- **`question_thread_variant_details`** — `GENERATED_PRACTICE` 1:1. `question_text/question_type/answer/explanation/source_sentence/source_question/difficulty` 구조화 저장(향후 문제 DB/WORKBOOK 승격 대비).
- **`question_thread_exam_evidence`** — `EXAM_EVIDENCE` 1:1. `school_name/grade_label/exam_period/related_sentence/exam_question_type/exam_point/description` + 향후 `exam_id` 선택 연결. 예상과 절대 같은 데이터로 취급하지 않는다.
- **`question_thread_attachments`** — `lesson_community_attachments` 형태. 글당 10개, 파일당 10MB, `DocumentUploadValidator.validateAttachment`.
- **`question_ai_summaries`** — 버전형 AI 종합(v1→v2). `status DRAFT|APPROVED`. 이번 PR 에는 write 경로 없음(빈 테이블) — Context 응답에 최신 `APPROVED` 포함만. AI Suggestion → Human Approval → New Version.

권한: 읽기 공개(비로그인), write(글/답글/수정/삭제/첨부)는 인증(STUDENT/TUTOR/ADMIN 누구나). 작성자만 수정, 작성자·ADMIN 만 숨김. 전부 `@AuthenticationPrincipal` 기준 — request body user id 불신뢰.

### 4.48 site_page_visits / wiki_download_logs ✅ 관리자 통합 Analytics (V962, V964~V966)

`docs/API.md §19` 참고. `academy_daily_visits`(§4.41)와 달리 날짜당 집계 row가 아니라 **1건 = 1 이벤트** 로그다 — PV는 row 수, UV는 `visitor_key` distinct count로 계산한다. 기존 `wiki_daily_visits`(V960, Wiki 운영 현황 카드 전용)는 그대로 유지되며 이 테이블들과 무관하게 독립 동작한다.

**`site_page_visits`** — F2R 전체 실제 페이지 진입(PV). `SiteVisitFilter`가 정적 리소스/API를 제외하고 응답이 `200` + `text/html`인 GET에서만 기록.

| column | type | note |
|---|---|---|
| site_page_visit_id | BIGINT PK AUTO_INCREMENT | |
| visit_date | DATE NOT NULL | `Asia/Seoul` 기준, 기간 집계용 |
| visited_at | DATETIME(6) NOT NULL | 최근 방문 목록 정렬용 |
| is_wiki | BIT(1) NOT NULL DEFAULT 0 | `path`가 `/wiki`로 시작하면 true(F2R 전체 집계에도 포함되는 부분집합) |
| path | VARCHAR(255) NOT NULL | 쿼리스트링 제외 |
| user_id | BIGINT NULL | 비로그인 NULL. FK 제약 없음(스냅샷 로그) |
| role | VARCHAR(20) NOT NULL | STUDENT\|TUTOR\|ADMIN\|GUEST |
| display_name | VARCHAR(60) NOT NULL | 방문 시점 `users.name`(실명) 스냅샷, 비회원 "비회원" |
| visitor_key | VARCHAR(64) NOT NULL | UV 식별자: 로그인 `"U:{userId}"`, 비로그인 `"V:{90일 cookie UUID}"`; legacy `"S:{session id}"` 호환. IP/fingerprint 미저장 |
| service_area | VARCHAR(16) NOT NULL | HOME\|CHART\|WEB\|ACADEMY\|WIKI\|OTHER. 기존 row는 path로 backfill |
| traffic_kind | VARCHAR(10) NOT NULL | HUMAN\|BOT. 일반 UV/PV는 HUMAN만, BOT 요청 수는 별도 집계 |

**`wiki_download_logs`** — Wiki 자료(ORIGINAL/ANALYSIS/LEARNING) 다운로드 이벤트. 다운로드 endpoint가 파일을 정상적으로 읽어온 시점에만 기록(버튼 클릭 카운트 아님).

| column | type | note |
|---|---|---|
| wiki_download_log_id | BIGINT PK AUTO_INCREMENT | |
| download_date | DATE NOT NULL | 기간 집계용 |
| downloaded_at | DATETIME(6) NOT NULL | 최근 다운로드 목록 정렬용 |
| material_category | VARCHAR(20) NOT NULL | ORIGINAL\|ANALYSIS\|LEARNING |
| material_id | BIGINT NOT NULL | 각 카테고리 테이블의 PK(FK 제약 없음) |
| material_title | VARCHAR(255) NOT NULL | 다운로드 시점 제목 스냅샷 |
| user_id | BIGINT NULL | 비회원 다운로드 허용 자료(ORIGINAL/ANALYSIS)는 NULL 가능 |
| role | VARCHAR(20) NOT NULL | STUDENT\|TUTOR\|ADMIN\|GUEST |
| display_name | VARCHAR(60) NOT NULL | 다운로드 시점 `users.name`(실명) 스냅샷 |

인덱스: `site_page_visits`는 기존 인덱스에 `(service_area, traffic_kind, visit_date)`, `(traffic_kind, visit_date)`가 추가된다. `wiki_download_logs`는 `(download_date)`, `(material_category, material_id)`, `(user_id)` — 관리자 화면 집계 쿼리가 기존 페이지 응답을 늦추지 않도록.

# 내신 Wiki V1 최소 데이터 확장

- `exam_material_sets`: 학교 프린트와 학생 오답의 원본 BLOB + canonical JSON 세트. `core_type`과
  `school/grade/year/semester/exam`, 선택적 `student_id`로 기존 시험·학생 관계를 보존한다.
- `educator_insights`: 기존 row를 유지하며 nullable `year`, `semester`, `exam`과
  `source(director/school_teacher)`를 추가한다. 기존 인사이트는 `source=director`, Context 미지정으로 호환한다.
## Wiki workspace isolation (FastAPI SQLite)

`wiki_workspaces` and `wiki_workspace_members` live in the Wiki workspace catalog SQLite file. The fixed `legacy` workspace points to the existing Production Wiki database and has only the authenticated `admin` and `modu` members. A non-legacy member receives a deterministic private workspace ID and a separate empty SQLite database on first access.

Every user-owned Wiki table is migrated idempotently with a nullable `workspace_id` column, backfilled without changing row counts, indexed, and protected by an insert trigger that writes the database workspace ID. The isolated database file is the primary security boundary; therefore a resource ID from another workspace cannot resolve even if numeric IDs collide.

Covered tables: `digital_twin_profiles`, `generated_questions`, `exam_papers`, `exam_draft`, `students`, `student_wrong_notes`, `educator_insights`, `exam_material_sets`, `final_deliverables`, CSAT source/question/wrong/assessment/item/usage tables, `source_materials`, `generated_tests`, and `ai_ab_evaluations`.

### exam_material_sets — PAST_EXAM + AI_WORK_LOG 확장

기존 workspace별 원본 BLOB 저장 계약을 유지한다. `material_type`은 `SCHOOL_PRINT`, `STUDENT_WRONG_NOTE`, `PAST_EXAM`, `AI_WORK_LOG`를 구분하고, 기존 행은 core_type에 따라 안전하게 backfill한다. `PAST_EXAM`은 실제 시행된 학교 기출 원본이며 `year`가 시험연도, `uploaded_by`와 `created_at`이 업로더/업로드 시각 provenance다. 같은 학교·학년·학기·시험에서도 연도별·파일별 독립 행을 저장하므로 새 업로드가 이전 기출을 덮어쓰지 않는다.

`body_text`/`body_format` 컬럼은 업로드에서 추출한 본문과 포맷(`html`|`markdown`|`text`)을 담는다. HTML은 `_sanitize_exam_html`(표준 라이브러리 `html.parser` 기반 allowlist 정제기, idempotent)로 `<script>`/`<style>`/편집기 toolbar·화면전용 UI를 제거하고 제목·문항·지문·선택지·표·보기·밑줄/강조·서답형 작성요령만 남긴 정제본을 저장한다. MD/TXT는 원문 그대로다. `PAST_EXAM` HTML/MD와 `AI_WORK_LOG`(MD/MARKDOWN/TXT/HTML, 파일당 10MB)가 이 컬럼을 채우고, PDF·이미지·HWP(X)는 빈 값으로 둔다. **원본 파일(`original_blob`)은 정제와 무관하게 그대로 보존한다.** `AI_WORK_LOG`는 Claude/ChatGPT 등이 시험 준비 과정에서 남긴 작업 로그로, FINAL Context의 `ai_work_logs`로 노출되어 다음 세션에서 MCP가 이어서 읽는다.

FINAL Context는 `past_exams`+`ai_work_logs` 본문을 항목당 200,000자·합산 600,000자(`_CONTEXT_BODY_TOTAL_BUDGET`)로 제한하고 저장 순서(year DESC, id DESC)대로 deterministic 하게 배분하며, 축약·제외분을 응답 `context_budget.truncated_items`/`omitted_items`로 명시한다(silent omission 금지). SQLite 시작 migration은 컬럼 추가와 backfill만 수행하며 기존 자료/경로를 변경하지 않는다.


### weekly_exam_sets / weekly_exam_audit

학교별 주간 실전 모의고사 완성 세트와 승인 이력을 저장하는 workspace 소유 SQLite 테이블이다. `exam_material_sets`는 학교자료·기출·오답·AI 작업기록의 Living Context이므로 산출물 상태를 섞지 않는다. 세트는 school/grade/year/semester/exam/round/set_type/question_count, 문제지·정답해설·검수 MD 본문과 SHA-256, source material ID 목록, immutable context_snapshot/context_hash, version/status, 생성·검토·공개 주체/시각을 가진다. status는 REVIEW, CHANGES_REQUESTED, REJECTED, APPROVED, FINAL, PUBLISHED이며 승인 전이는 서버 ADMIN Gate를 거친다. `weekly_exam_audit`는 등록과 상태 전이를 append-only로 기록한다.
