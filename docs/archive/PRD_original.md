# 과외 솔루션 서비스 기획서 (PRD)

## 1. 서비스 개요

개인 과외 강사가 담당 학생들의 학습 데이터를 체계적으로 관리하고, 오답 누적 및 성장 곡선을 시각화할 수 있는 **과외 관리 플랫폼**이다.

### 핵심 가치
- 강사: 학생별 학습 현황을 한눈에 파악하고, 맞춤``형 문제를 제공
- 학생: 오답 분석과 성장 데이터를 통해 학습 동기 부여
- 학부모: 자녀의 학습 진도와 성적 변화를 투명하게 확인``

---

## 2. 사용자 역할 (Role)``

| 역할 | 설명 |
|------|------|
| `TUTOR` | 강사. 학생 등록, 시험지 업로드, 문제 출제, 성적 입력을 수행 |
| `STUDENT` | 학생. 문제 풀이, 학습 이력 조회 가능 |
| `PARENT` | 학부모. 자녀의 성적 및 학습 현황 열람 가능 |

---

## 3. 주요 도메인 및 플로우

### 3.1 회원 관리

학생 계정 생성 방식은 두 가지를 모두 지원한다.

**방식 A: 강사가 학생 계정 직접 생성**
```
[강사 가입/로그인] → 강사가 학생 계정 생성 → 강사-학생 과목별 매핑(TutorStudentMap)
```

**방식 B: 학생이 직접 가입**
```
[학생 직접 가입] → 강사가 해당 학생을 검색 후 매핑 → 강사-학생 과목별 매핑(TutorStudentMap)
```

**공통**
```
학부모 계정 생성 후 학생에 연결 (선택)
```

- 모든 사용자는 `users` 테이블에 통합 관리 (`login_id`, `password`, `name`, `phone`, `role`)
- 학생은 `students` 테이블에 추가 정보 저장 (`school_name`, `grade`, `birth_date`, `parent_id`)
- 강사 한 명이 여러 학생을 담당하고, 과목(subject)별로 매핑

---

### 3.2 과목 및 카테고리 관리

```
Subject (과목: 국어, 수학, 영어 등)
  └── QuestionCategory (세부 유형: 독해, 문법, 함수 등)
```

- 과목은 `name_en` / `name_ko` 두 가지 언어로 관리
- 카테고리는 과목에 종속되며, 문제 출제 시 분류 기준으로 사용

---

### 3.3 시험지 업로드 플로우

```
강사 → 시험지(PDF/이미지) 업로드
     → ExamPaper 생성 (학생, 과목, 강사 정보 포함)
     → 파일 저장 (original_file_name, stored_file_name, file_path)
     → 시험 날짜 및 설명 기록
```

- 강사가 학생별로 시험지를 업로드
- 파일 타입: PDF 또는 IMAGE
- `exam_date`로 시험 이력 관리

---

### 3.4 문제 관리 플로우 ⚠️ MVP 제외 (추후 구현)

```
[지문(Passage) 등록] (선택)
        ↓
[문제(Question) 등록]
  - 과목, 카테고리, 난이도(1~5) 설정
  - 정답 및 해설 포함
  - 지문이 있으면 연결 (독해 문제 등)
```

- 지문 없이 독립 문제 출제 가능 (`passage_id` nullable)
- 난이도: 1(쉬움) ~ 5(어려움), 기본값 3
- 출처(`source`) 필드로 EBS, 기출 등 구분
- **MVP에서는 구현하지 않으며, Entity/스키마는 미리 정의해둔 상태**

---

### 3.5 문제 풀이 및 학습 이력 기록 플로우

```
학생이 문제 풀이
  → LearningLog 생성
    - 학생 답안 (student_answer)
    - 정오답 여부 (is_correct)
    - 풀이 소요 시간 (time_spent, 초 단위)
    - 풀이 시각 (solved_at)
```

- 모든 풀이 이력이 누적되어 오답 분석에 활용
- 동일 문제를 반복 풀이해도 매번 새로운 로그 생성 → 성취도 변화 추적 가능

---

### 3.6 성적 관리 및 성장 곡선 플로우

```
강사 → 시험 결과 입력 (StudentScore)
  - 시험명, 과목, 점수, 목표 점수, 시험 날짜
  → 시간 흐름에 따른 점수 변화 그래프 (성장 곡선) 시각화
```

- 과목별로 성적 이력 누적
- `target_score`와 `score` 비교를 통해 목표 달성률 추적

---

## 4. 전체 서비스 플로우 요약

```
1. 회원 가입 및 로그인
   - 강사 가입
   - 학생 직접 가입 또는 강사가 학생 계정 생성
   - 학부모 가입 후 학생에 연결 (선택)

2. 강사-학생 과목 매핑 (TutorStudentMap)
   - 강사가 학생을 검색하거나 직접 생성한 후 과목별로 연결

3. 시험지 업로드 (PDF/이미지) [MVP 포함]

4. 강사가 시험 성적 입력 [MVP 포함]

5. 대시보드에서 확인 [MVP 포함]
   - 성장 곡선 (과목별 점수 추이 그래프)
   - 시험지 이력 조회

--- 이하 MVP 제외 ---

6. 문제 출제 (지문 + 문제 등록) [MVP 제외]
7. 학생 문제 풀이 → 학습 이력 자동 기록 [MVP 제외]
8. 오답 분석 대시보드 (카테고리별 약점 파악) [MVP 제외]
```

---

## 5. 데이터 모델 관계 요약

```
users ──────────── students (1:1)
  │                    │
  │ (tutor)            │ (student)
  └─── tutor_student_map ───┘
              │
          subject_id → subjects
                            │
                    question_categories

students ── student_scores (N:1) → subjects
students ── learning_logs (N:1) → questions
students ── exam_papers (N:1) → subjects

questions ── passages (N:1, optional)
questions ── subjects
questions ── question_categories
```

---

## 6. 기술 아키텍처

### 6.1 서버 구성

```
[브라우저]
    ↕
[Spring Boot]  ← BFF(Backend for Frontend) + SSR 담당
    │  - Thymeleaf 렌더링 (MVP)
    │  - 파일 수신 및 raw 파일 저장
    │  - raw_imgs 테이블에 파일 경로 기록 (유일하게 직접 DB 접근)
    │  - 인증/인가 처리 (MVP에서는 미구현, permitAll 상태)
    │  - 데이터 조회는 Python API 경유
    ↕
[Python 서버]  ← 실제 비즈니스 로직 + DB 전담
    │  - 모든 비즈니스 데이터 CRUD (성적, 학생, 문제 등)
    │  - LLM API 호출 (Claude 또는 Gemini)
    │  - 시험지 분석 결과 DB 저장
    ↕
[DB] + [LLM API (Claude / Gemini)]
```

### 6.2 역할 분리 원칙

| 레이어 | 역할 | DB 접근 |
|--------|------|---------|
| Spring Boot | 파일 수신, SSR 렌더링, Python API 중계, 인증(추후) | raw_imgs 테이블만 직접 접근 |
| Python | 비즈니스 로직, LLM 호출, 분석 결과 저장 | 전체 DB 전담 |

### 6.3 시험지 처리 플로우

```
강사 → 시험지 업로드 (이미지/PDF)
  → Spring Boot: 파일 저장 + raw_imgs 테이블에 경로 기록
  → Python: raw_imgs 읽기 → LLM API (OCR + 문제 구조화)
  → Python: 분석 결과 DB 저장 (passage, questions, student_score 등)
  → Spring Boot: 처리 완료 응답 수신
```

- LLM API는 Claude 또는 Gemini 사용 (교체 가능하도록 Python 내부에서 추상화)
- LLM 호출은 수십 초 소요될 수 있어 **비동기 처리** 적용 (처리 상태: PENDING → PROCESSING → DONE)

### 6.4 프론트엔드 전환 계획

| 단계 | 프론트엔드 | 비고 |
|------|-----------|------|
| MVP | Thymeleaf (Spring Boot SSR) | 빠른 기능 검증 목적 |
| 이후 | React (SPA) | Spring Boot 제거 또는 축소, Python API 직접 호출 |

### 6.5 인증/인가

- **MVP에서는 미구현** — Spring Security는 추가된 상태이나 `anyRequest().permitAll()` 로 전체 허용
- 추후 JWT 필터를 `SecurityConfig.java`에 추가하는 방식으로 확장
- Spring Boot가 인증 담당, Python은 내부 요청만 신뢰하는 구조로 설계 예정

---

## 7. 추후 고려사항

- 학부모 알림: 학생의 풀이 완료 또는 성적 등록 시 학부모에게 푸시 알림
- AI 오답 분석: 반복 오답 패턴을 분석하여 취약 유형 자동 추천
- 문제 북마크: 학생이 어렵다고 표시한 문제 별도 관리
- 과제 기능: 강사가 학생에게 특정 문제 세트를 숙제로 지정
- JWT 인증 도입: Spring Boot에서 JWT 발급/검증, Python API 호출 시 내부 토큰 첨부
