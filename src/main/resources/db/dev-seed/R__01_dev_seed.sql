-- ============================================================
-- V900 (dev-seed): 로컬 개발용 시드
--   · admin / student 시드 계정 (회원가입 없이 바로 로그인 테스트)
--   · 학생 더미 시계열 시험 데이터 (대시보드 radar/line 검증용)
--
-- ⚠ 운영(prd) 환경에는 적용되지 않는다.
--   spring.flyway.locations가 prd에서는 classpath:db/migration만 가리키도록 분기되어 있음.
--   (운영 admin 계정은 첫 배포 후 별도 절차로 생성)
--
-- ⚠ V900 / V901로 분리하지 않은 이유: 두 블록이 LAST_INSERT_ID() 흐름으로 연결되어 있어
--   파일을 끊으면 컨텍스트가 끊긴다. 하나의 마이그레이션으로 묶어 두는 게 안전.
-- ============================================================

-- ============================================================
-- (1) Admin 시드 계정
--     비밀번호: admin1234 (BCrypt 해시)
-- ============================================================
INSERT INTO `users` (`login_id`, `password`, `name`, `nickname`, `phone`, `role`) VALUES
    ('admin', '$2a$10$b1zH0Y4x2dC7YMdGjrWCzuSF4gRpiVnnt2B1s3jA2HPBP3xrvc9Di', '관리자', 'admin', NULL, 'ADMIN');

-- ============================================================
-- (2) 테스트 학생 시드 계정
--     로그인 ID: student / 비밀번호: admin1234
--     학교: 현대고등학교 / 2학년
-- ============================================================
INSERT INTO `users` (`login_id`, `password`, `name`, `nickname`, `email`, `phone`, `role`) VALUES
    ('student', '$2a$10$b1zH0Y4x2dC7YMdGjrWCzuSF4gRpiVnnt2B1s3jA2HPBP3xrvc9Di',
     '테스트학생', 'student', 'test_student@example.com', '010-0000-0000', 'STUDENT');

INSERT INTO `students` (`user_id`, `school_name`, `grade`, `parent_name`, `parent_phone`) VALUES
    (LAST_INSERT_ID(), '현대고등학교', 2, '테스트학부모', '010-1111-2222');

-- ============================================================
-- (3) student 더미 시계열 데이터 (대시보드 radar/line 검증용)
--     · 시험 4건 (1학년 1학기 중간/기말, 1학년 2학기 중간/기말 — 2학년 현재 시점에서 과거 시계열)
--     · 각 시험마다 questions 10개 (vocab 3, grammar 3, reading 3, memorization 1)
--     · 시계열: 60% → 70% → 80% → 90% (점진적 성장)
--     · 카테고리 편차: 어휘/암기 강함, 독해 약점 (radar에 잘 드러나는 패턴)
--     · 마지막 시험(2024 2학기 기말고사)만 conversation에 첨부 → dashboard radar 기준
--
--     ⚠ source.storage_key는 가짜 경로 — 라이트박스 미리보기는 깨지지만 분석/카드 표시는 정상.
-- ============================================================

SET @std_id := LAST_INSERT_ID();
SET @std_user_id := (SELECT `user_id` FROM `students` WHERE `student_id` = @std_id);
SET @subj := (SELECT `subject_id` FROM `subjects` WHERE `name_en`='english');
SET @cat_voc  := (SELECT `category_id` FROM `question_categories` WHERE `category_name_en`='vocabulary');
SET @cat_gram := (SELECT `category_id` FROM `question_categories` WHERE `category_name_en`='grammar');
SET @cat_mem  := (SELECT `category_id` FROM `question_categories` WHERE `category_name_en`='memorization');
SET @cat_read := (SELECT `category_id` FROM `question_categories` WHERE `category_name_en`='reading');

-- ── 시험 1: 2024 1학기 중간고사 (60%) ──────────────────────
INSERT INTO `exams` (`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `subject_id`, `display_name`, `exam_date`)
VALUES ('현대고등학교', 1, 'MIDTERM', 2024, 1, @subj, '현대고등학교 1학년 2024 1학기 중간고사', '2024-04-25');
SET @e1 := LAST_INSERT_ID();

INSERT INTO `sources` (`uploaded_by`, `original_file_name`, `storage_key`, `file_type`, `file_size`, `source_type`, `content_kind`, `processing_status`, `exam_id`, `uploaded_at`)
VALUES (@std_user_id, '2024_1학기_중간고사.pdf', '/storage/dummy/exam1.pdf', 'PDF', 0, 'STUDENT_MATERIAL', 'EXAM_BLANK', 'COMPLETED', @e1, '2024-04-26 10:00:00');
SET @s1 := LAST_INSERT_ID();

INSERT INTO `questions` (`subject_id`, `category_id`, `exam_id`, `question_type`, `question_text`, `correct_answer`, `source`, `order_num`, `question_label`) VALUES
  (@subj, @cat_voc,  @e1, 'MULTIPLE_CHOICE', '어휘 문제 1',  '1', 'READFILE',  1,  '1'),
  (@subj, @cat_voc,  @e1, 'MULTIPLE_CHOICE', '어휘 문제 2',  '1', 'READFILE',  2,  '2'),
  (@subj, @cat_voc,  @e1, 'MULTIPLE_CHOICE', '어휘 문제 3',  '1', 'READFILE',  3,  '3'),
  (@subj, @cat_gram, @e1, 'MULTIPLE_CHOICE', '문법 문제 1',  '1', 'READFILE',  4,  '4'),
  (@subj, @cat_gram, @e1, 'MULTIPLE_CHOICE', '문법 문제 2',  '1', 'READFILE',  5,  '5'),
  (@subj, @cat_gram, @e1, 'MULTIPLE_CHOICE', '문법 문제 3',  '1', 'READFILE',  6,  '6'),
  (@subj, @cat_read, @e1, 'MULTIPLE_CHOICE', '독해 문제 1',  '1', 'READFILE',  7,  '7'),
  (@subj, @cat_read, @e1, 'MULTIPLE_CHOICE', '독해 문제 2',  '1', 'READFILE',  8,  '8'),
  (@subj, @cat_read, @e1, 'MULTIPLE_CHOICE', '독해 문제 3',  '1', 'READFILE',  9,  '9'),
  (@subj, @cat_mem,  @e1, 'MULTIPLE_CHOICE', '암기 문제 1',  '1', 'READFILE', 10, '10');
SET @q1 := LAST_INSERT_ID();
-- 시험1 점수 6/10 — vocab 2/3, grammar 2/3, reading 1/3, memorization 1/1
INSERT INTO `student_answers` (`student_id`, `question_id`, `exam_id`, `student_answer`, `is_correct`) VALUES
  (@std_id, @q1+0, @e1, '1', TRUE),  (@std_id, @q1+1, @e1, '1', TRUE),  (@std_id, @q1+2, @e1, '2', FALSE),
  (@std_id, @q1+3, @e1, '1', TRUE),  (@std_id, @q1+4, @e1, '1', TRUE),  (@std_id, @q1+5, @e1, '2', FALSE),
  (@std_id, @q1+6, @e1, '1', TRUE),  (@std_id, @q1+7, @e1, '2', FALSE), (@std_id, @q1+8, @e1, '3', FALSE),
  (@std_id, @q1+9, @e1, '1', TRUE);
INSERT INTO `student_scores` (`student_id`, `exam_id`, `source_id`, `total_questions`, `correct_count`, `confirmed_at`)
VALUES (@std_id, @e1, @s1, 10, 6, '2024-04-26 10:30:00');

-- ── 시험 2: 2024 1학기 기말고사 (70%) ──────────────────────
INSERT INTO `exams` (`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `subject_id`, `display_name`, `exam_date`)
VALUES ('현대고등학교', 1, 'FINAL', 2024, 1, @subj, '현대고등학교 1학년 2024 1학기 기말고사', '2024-06-28');
SET @e2 := LAST_INSERT_ID();

INSERT INTO `sources` (`uploaded_by`, `original_file_name`, `storage_key`, `file_type`, `file_size`, `source_type`, `content_kind`, `processing_status`, `exam_id`, `uploaded_at`)
VALUES (@std_user_id, '2024_1학기_기말고사.pdf', '/storage/dummy/exam2.pdf', 'PDF', 0, 'STUDENT_MATERIAL', 'EXAM_BLANK', 'COMPLETED', @e2, '2024-06-29 10:00:00');
SET @s2 := LAST_INSERT_ID();

INSERT INTO `questions` (`subject_id`, `category_id`, `exam_id`, `question_type`, `question_text`, `correct_answer`, `source`, `order_num`, `question_label`) VALUES
  (@subj, @cat_voc,  @e2, 'MULTIPLE_CHOICE', '어휘 문제 1',  '1', 'READFILE',  1,  '1'),
  (@subj, @cat_voc,  @e2, 'MULTIPLE_CHOICE', '어휘 문제 2',  '1', 'READFILE',  2,  '2'),
  (@subj, @cat_voc,  @e2, 'MULTIPLE_CHOICE', '어휘 문제 3',  '1', 'READFILE',  3,  '3'),
  (@subj, @cat_gram, @e2, 'MULTIPLE_CHOICE', '문법 문제 1',  '1', 'READFILE',  4,  '4'),
  (@subj, @cat_gram, @e2, 'MULTIPLE_CHOICE', '문법 문제 2',  '1', 'READFILE',  5,  '5'),
  (@subj, @cat_gram, @e2, 'MULTIPLE_CHOICE', '문법 문제 3',  '1', 'READFILE',  6,  '6'),
  (@subj, @cat_read, @e2, 'MULTIPLE_CHOICE', '독해 문제 1',  '1', 'READFILE',  7,  '7'),
  (@subj, @cat_read, @e2, 'MULTIPLE_CHOICE', '독해 문제 2',  '1', 'READFILE',  8,  '8'),
  (@subj, @cat_read, @e2, 'MULTIPLE_CHOICE', '독해 문제 3',  '1', 'READFILE',  9,  '9'),
  (@subj, @cat_mem,  @e2, 'MULTIPLE_CHOICE', '암기 문제 1',  '1', 'READFILE', 10, '10');
SET @q2 := LAST_INSERT_ID();
-- 시험2 점수 7/10 — vocab 2/3, grammar 2/3, reading 2/3, memorization 1/1
INSERT INTO `student_answers` (`student_id`, `question_id`, `exam_id`, `student_answer`, `is_correct`) VALUES
  (@std_id, @q2+0, @e2, '1', TRUE),  (@std_id, @q2+1, @e2, '1', TRUE),  (@std_id, @q2+2, @e2, '2', FALSE),
  (@std_id, @q2+3, @e2, '1', TRUE),  (@std_id, @q2+4, @e2, '1', TRUE),  (@std_id, @q2+5, @e2, '2', FALSE),
  (@std_id, @q2+6, @e2, '1', TRUE),  (@std_id, @q2+7, @e2, '1', TRUE),  (@std_id, @q2+8, @e2, '3', FALSE),
  (@std_id, @q2+9, @e2, '1', TRUE);
INSERT INTO `student_scores` (`student_id`, `exam_id`, `source_id`, `total_questions`, `correct_count`, `confirmed_at`)
VALUES (@std_id, @e2, @s2, 10, 7, '2024-06-29 10:30:00');

-- ── 시험 3: 2024 2학기 중간고사 (80%) ──────────────────────
INSERT INTO `exams` (`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `subject_id`, `display_name`, `exam_date`)
VALUES ('현대고등학교', 1, 'MIDTERM', 2024, 2, @subj, '현대고등학교 1학년 2024 2학기 중간고사', '2024-10-15');
SET @e3 := LAST_INSERT_ID();

INSERT INTO `sources` (`uploaded_by`, `original_file_name`, `storage_key`, `file_type`, `file_size`, `source_type`, `content_kind`, `processing_status`, `exam_id`, `uploaded_at`)
VALUES (@std_user_id, '2024_2학기_중간고사.pdf', '/storage/dummy/exam3.pdf', 'PDF', 0, 'STUDENT_MATERIAL', 'EXAM_BLANK', 'COMPLETED', @e3, '2024-10-16 10:00:00');
SET @s3 := LAST_INSERT_ID();

INSERT INTO `questions` (`subject_id`, `category_id`, `exam_id`, `question_type`, `question_text`, `correct_answer`, `source`, `order_num`, `question_label`) VALUES
  (@subj, @cat_voc,  @e3, 'MULTIPLE_CHOICE', '어휘 문제 1',  '1', 'READFILE',  1,  '1'),
  (@subj, @cat_voc,  @e3, 'MULTIPLE_CHOICE', '어휘 문제 2',  '1', 'READFILE',  2,  '2'),
  (@subj, @cat_voc,  @e3, 'MULTIPLE_CHOICE', '어휘 문제 3',  '1', 'READFILE',  3,  '3'),
  (@subj, @cat_gram, @e3, 'MULTIPLE_CHOICE', '문법 문제 1',  '1', 'READFILE',  4,  '4'),
  (@subj, @cat_gram, @e3, 'MULTIPLE_CHOICE', '문법 문제 2',  '1', 'READFILE',  5,  '5'),
  (@subj, @cat_gram, @e3, 'MULTIPLE_CHOICE', '문법 문제 3',  '1', 'READFILE',  6,  '6'),
  (@subj, @cat_read, @e3, 'MULTIPLE_CHOICE', '독해 문제 1',  '1', 'READFILE',  7,  '7'),
  (@subj, @cat_read, @e3, 'MULTIPLE_CHOICE', '독해 문제 2',  '1', 'READFILE',  8,  '8'),
  (@subj, @cat_read, @e3, 'MULTIPLE_CHOICE', '독해 문제 3',  '1', 'READFILE',  9,  '9'),
  (@subj, @cat_mem,  @e3, 'MULTIPLE_CHOICE', '암기 문제 1',  '1', 'READFILE', 10, '10');
SET @q3 := LAST_INSERT_ID();
-- 시험3 점수 8/10 — vocab 3/3, grammar 2/3, reading 2/3, memorization 1/1
INSERT INTO `student_answers` (`student_id`, `question_id`, `exam_id`, `student_answer`, `is_correct`) VALUES
  (@std_id, @q3+0, @e3, '1', TRUE),  (@std_id, @q3+1, @e3, '1', TRUE),  (@std_id, @q3+2, @e3, '1', TRUE),
  (@std_id, @q3+3, @e3, '1', TRUE),  (@std_id, @q3+4, @e3, '1', TRUE),  (@std_id, @q3+5, @e3, '2', FALSE),
  (@std_id, @q3+6, @e3, '1', TRUE),  (@std_id, @q3+7, @e3, '1', TRUE),  (@std_id, @q3+8, @e3, '3', FALSE),
  (@std_id, @q3+9, @e3, '1', TRUE);
INSERT INTO `student_scores` (`student_id`, `exam_id`, `source_id`, `total_questions`, `correct_count`, `confirmed_at`)
VALUES (@std_id, @e3, @s3, 10, 8, '2024-10-16 10:30:00');

-- ── 시험 4: 2024 2학기 기말고사 (90%) — conversation에 첨부할 시험 ──
INSERT INTO `exams` (`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `subject_id`, `display_name`, `exam_date`)
VALUES ('현대고등학교', 1, 'FINAL', 2024, 2, @subj, '현대고등학교 1학년 2024 2학기 기말고사', '2024-12-20');
SET @e4 := LAST_INSERT_ID();

INSERT INTO `sources` (`uploaded_by`, `original_file_name`, `storage_key`, `file_type`, `file_size`, `source_type`, `content_kind`, `processing_status`, `exam_id`, `uploaded_at`)
VALUES (@std_user_id, '2024_2학기_기말고사.pdf', '/storage/dummy/exam4.pdf', 'PDF', 0, 'STUDENT_MATERIAL', 'EXAM_BLANK', 'COMPLETED', @e4, '2024-12-21 10:00:00');
SET @s4 := LAST_INSERT_ID();

INSERT INTO `questions` (`subject_id`, `category_id`, `exam_id`, `question_type`, `question_text`, `correct_answer`, `source`, `order_num`, `question_label`) VALUES
  (@subj, @cat_voc,  @e4, 'MULTIPLE_CHOICE', '어휘 문제 1',  '1', 'READFILE',  1,  '1'),
  (@subj, @cat_voc,  @e4, 'MULTIPLE_CHOICE', '어휘 문제 2',  '1', 'READFILE',  2,  '2'),
  (@subj, @cat_voc,  @e4, 'MULTIPLE_CHOICE', '어휘 문제 3',  '1', 'READFILE',  3,  '3'),
  (@subj, @cat_gram, @e4, 'MULTIPLE_CHOICE', '문법 문제 1',  '1', 'READFILE',  4,  '4'),
  (@subj, @cat_gram, @e4, 'MULTIPLE_CHOICE', '문법 문제 2',  '1', 'READFILE',  5,  '5'),
  (@subj, @cat_gram, @e4, 'MULTIPLE_CHOICE', '문법 문제 3',  '1', 'READFILE',  6,  '6'),
  (@subj, @cat_read, @e4, 'MULTIPLE_CHOICE', '독해 문제 1',  '1', 'READFILE',  7,  '7'),
  (@subj, @cat_read, @e4, 'MULTIPLE_CHOICE', '독해 문제 2',  '1', 'READFILE',  8,  '8'),
  (@subj, @cat_read, @e4, 'MULTIPLE_CHOICE', '독해 문제 3',  '1', 'READFILE',  9,  '9'),
  (@subj, @cat_mem,  @e4, 'MULTIPLE_CHOICE', '암기 문제 1',  '1', 'READFILE', 10, '10');
SET @q4 := LAST_INSERT_ID();
-- 시험4 점수 9/10 — vocab 3/3, grammar 3/3, reading 2/3, memorization 1/1 → radar에 독해 약점이 드러남
INSERT INTO `student_answers` (`student_id`, `question_id`, `exam_id`, `student_answer`, `is_correct`) VALUES
  (@std_id, @q4+0, @e4, '1', TRUE),  (@std_id, @q4+1, @e4, '1', TRUE),  (@std_id, @q4+2, @e4, '1', TRUE),
  (@std_id, @q4+3, @e4, '1', TRUE),  (@std_id, @q4+4, @e4, '1', TRUE),  (@std_id, @q4+5, @e4, '1', TRUE),
  (@std_id, @q4+6, @e4, '1', TRUE),  (@std_id, @q4+7, @e4, '1', TRUE),  (@std_id, @q4+8, @e4, '2', FALSE),
  (@std_id, @q4+9, @e4, '1', TRUE);
INSERT INTO `student_scores` (`student_id`, `exam_id`, `source_id`, `total_questions`, `correct_count`, `confirmed_at`)
VALUES (@std_id, @e4, @s4, 10, 9, '2024-12-21 10:30:00');

-- ── 대화 1건 — 시험4 첨부 (radar는 이 대화 기준으로 시험4 카테고리별 정답률을 그린다) ──
INSERT INTO `conversations` (`student_id`, `title`, `last_message_at`)
VALUES (@std_id, '2024 2학기 기말고사 복기', '2024-12-22 14:00:00');
SET @conv := LAST_INSERT_ID();

INSERT INTO `conversation_sources` (`conversation_id`, `source_id`) VALUES (@conv, @s4);
