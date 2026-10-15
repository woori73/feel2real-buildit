-- ============================================================
-- V1: 초기 스키마 + 마스터 시드
--   · 모든 CREATE TABLE (운영·로컬 공통)
--   · subjects / question_categories 마스터 시드 (운영에도 반드시 필요)
--   · 시드 계정(admin/student) 및 더미 시험 데이터는 V900 (dev-seed)로 분리
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 1. 과목 (Subject)
-- ============================================================
CREATE TABLE `subjects` (
    `subject_id` INT AUTO_INCREMENT PRIMARY KEY,
    `name_en` VARCHAR(50) NOT NULL UNIQUE,
    `name_ko` VARCHAR(50) NOT NULL UNIQUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `subjects` (`name_en`, `name_ko`) VALUES ('english', '영어');

-- ============================================================
-- 2. 과목별 카테고리 (QuestionCategory)
--    취약점 분석의 집계 기준
-- ============================================================
CREATE TABLE `question_categories` (
    `category_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `subject_id` INT NOT NULL,
    `category_name_ko` VARCHAR(50) NOT NULL,
    `category_name_en` VARCHAR(50) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`subject_id`) REFERENCES `subjects`(`subject_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 영어 카테고리 시드 (5종 — 어휘/문법/암기/독해 + 기타)
-- 'other'는 듣기/작문/말하기 등 4종에 정확히 끼지 않는 영역을 위한 폴백.
INSERT INTO `question_categories` (`subject_id`, `category_name_ko`, `category_name_en`) VALUES
    ((SELECT `subject_id` FROM `subjects` WHERE `name_en`='english'), '어휘',  'vocabulary'),
    ((SELECT `subject_id` FROM `subjects` WHERE `name_en`='english'), '문법',  'grammar'),
    ((SELECT `subject_id` FROM `subjects` WHERE `name_en`='english'), '암기',  'memorization'),
    ((SELECT `subject_id` FROM `subjects` WHERE `name_en`='english'), '독해',  'reading'),
    ((SELECT `subject_id` FROM `subjects` WHERE `name_en`='english'), '기타',  'other');

-- ============================================================
-- 3. 사용자 (User)
--    STUDENT / ADMIN 사용. TUTOR는 장기 비전(§0)을 위한 예약값.
-- ============================================================
CREATE TABLE `users` (
    `user_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `login_id` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `name` VARCHAR(30) NOT NULL,
    `email` VARCHAR(100),
    `phone` VARCHAR(20),
    `role` ENUM('STUDENT', 'ADMIN', 'TUTOR') NOT NULL DEFAULT 'STUDENT',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. 학생 상세 정보 (Student)
--    학부모 연락처를 직접 컬럼으로 저장 (별도 계정 없음)
-- ============================================================
CREATE TABLE `students` (
    `student_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL UNIQUE,
    `school_name` VARCHAR(100) NOT NULL,
    `grade` TINYINT NOT NULL,
    `parent_name` VARCHAR(30),
    `parent_phone` VARCHAR(20),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Exam (시험 마스터, v2 — dedup 단위)
--     학생 무관. 같은 시험은 row 1개만 존재.
--     dedup 키: (school_name, grade, exam_type, exam_year, exam_semester, subject_id)
-- ============================================================
CREATE TABLE `exams` (
    `exam_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `school_name` VARCHAR(100) NOT NULL,
    `grade` TINYINT NOT NULL,
    `exam_type` ENUM('MIDTERM', 'FINAL', 'MOCK', 'OTHER') NOT NULL,
    `exam_year` SMALLINT,
    `exam_semester` TINYINT,
    `subject_id` INT NOT NULL,
    `display_name` VARCHAR(200),
    `exam_date` DATE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_exams_dedup_key` (`school_name`, `grade`, `exam_type`, `exam_year`, `exam_semester`, `subject_id`),
    FOREIGN KEY (`subject_id`) REFERENCES `subjects`(`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. 업로드 자료 원본 저장소 (Source)
--    학생·Admin이 올리는 모든 파일. 형식 무관.
-- ============================================================
CREATE TABLE `sources` (
    `source_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `uploaded_by` BIGINT NOT NULL,
    `original_file_name` VARCHAR(255) NOT NULL,
    `storage_key` VARCHAR(500) NOT NULL,
    `file_type` VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'PDF / IMAGE / UNKNOWN',
    `file_size` BIGINT,
    `source_type` ENUM('STUDENT_MATERIAL', 'HOMEWORK_PROBLEM', 'HOMEWORK_ANSWER') NOT NULL,
    `content_kind` ENUM('GENERAL', 'EXAM_BLANK', 'EXAM_GRADED', 'UNCLASSIFIED') NOT NULL DEFAULT 'UNCLASSIFIED' COMMENT '학생 폼에서 GENERAL/EXAM_BLANK로 들어오고, 시험지는 LLM 분석 후 채점 흔적 감지 시 EXAM_GRADED로 promote됨 (§5.0 4단계)',
    `processing_status` ENUM('PENDING', 'ANALYZING', 'PENDING_REVIEW', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '분석 워크플로 상태. 채팅은 모든 첨부 source가 COMPLETED일 때만 허용 (§5.0)',
    `analysis_result` JSON COMMENT 'Phase 3 시험지 분석 임시 결과 (meta + questions + dedup matched_exam_id). Phase 4 확정 시 questions/answers/scores/exams에 INSERT됨. GENERAL은 NULL.',
    `exam_id` BIGINT COMMENT '시험지로 판별 + 학생 검증 후 연결되는 시험 마스터 (NULL=일반 source)',
    `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`uploaded_by`) REFERENCES `users`(`user_id`) ON DELETE CASCADE,
    FOREIGN KEY (`exam_id`) REFERENCES `exams`(`exam_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. 지문 (Passage)
--    독해 문제 생성 시 지문 저장
-- ============================================================
CREATE TABLE `passages` (
    `passage_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `subject_id` INT NOT NULL,
    `title` VARCHAR(255),
    `content` LONGTEXT NOT NULL,
    `source_origin` VARCHAR(255) COMMENT '출처 (EBS, 모의고사 등)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`subject_id`) REFERENCES `subjects`(`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. LLM 대화 세션 (Conversation)
--    창(탭) 1개 = 대화 1개. 첨부 파일이 컨텍스트 스코프를 결정.
-- ============================================================
CREATE TABLE `conversations` (
    `conversation_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `title` VARCHAR(200),
    `metadata` JSON COMMENT '대화 메타데이터: 학생정보 스냅샷, 과목, 연결된 source 요약 등 (§5.0 7단계)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_message_at` DATETIME,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. 대화-파일 매핑 (ConversationSource)
--    이 테이블에 연결된 파일만 해당 세션의 LLM 컨텍스트로 사용됨
-- ============================================================
CREATE TABLE `conversation_sources` (
    `conversation_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL,
    PRIMARY KEY (`conversation_id`, `source_id`),
    FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`conversation_id`) ON DELETE CASCADE,
    FOREIGN KEY (`source_id`) REFERENCES `sources`(`source_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 9. 대화 메시지 히스토리 (ConversationMessage)
--    기존 대화 재입장 시 전체를 Claude API에 전달
-- ============================================================
CREATE TABLE `conversation_messages` (
    `message_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` BIGINT NOT NULL,
    `role` ENUM('USER', 'ASSISTANT') NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`conversation_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. 문제 마스터 (Question)
--     1차 단계에서는 시험지 파일에서 추출된 문제(source=READFILE)만 INSERT된다.
--     category_id → 취약점 분석 집계 기준 (후속)
-- ============================================================
CREATE TABLE `questions` (
    `question_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `subject_id` INT NOT NULL,
    `category_id` BIGINT,
    `passage_id` BIGINT,
    `exam_id` BIGINT COMMENT '시험지에서 추출된 문제 (exams.exam_id)',
    `question_type` ENUM('MULTIPLE_CHOICE', 'SHORT_ANSWER') NOT NULL,
    `question_text` TEXT NOT NULL,
    `choices` JSON,
    `correct_answer` VARCHAR(500),
    `explanation` TEXT,
    `source` ENUM('READFILE', 'GENERATED', 'MANUAL') NOT NULL COMMENT 'READFILE=파일 추출(1차), GENERATED=LLM 생성(진단 등 후속), MANUAL=Admin 직접 입력(후속)',
    `order_num` INT NOT NULL DEFAULT 1 COMMENT '세션 내 출제 순번 (정렬용)',
    `question_label` VARCHAR(50) COMMENT '시험지에 적힌 그대로의 문항 라벨 (예: "1", "24", "[서답형 1] (1)"). 검증 페이지/표시용. nullable — LLM이 못 뽑으면 NULL.',
    `point` INT NOT NULL DEFAULT 0 COMMENT '문항 배점. LLM이 시험지에서 추출 → 학생 검증 단계에서 수정 가능. 0이면 점수 합산에서 제외 (=배점 미명시 문제).',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`subject_id`) REFERENCES `subjects`(`subject_id`),
    FOREIGN KEY (`category_id`) REFERENCES `question_categories`(`category_id`) ON DELETE SET NULL,
    FOREIGN KEY (`passage_id`) REFERENCES `passages`(`passage_id`) ON DELETE SET NULL,
    FOREIGN KEY (`exam_id`) REFERENCES `exams`(`exam_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. 통합 답안 (StudentAnswer)
--     1차 단계에서는 시험지 답안(exam_id 채워짐)만 들어온다.
--     questions.category_id 와 JOIN → 카테고리별 취약점 집계
-- ============================================================
CREATE TABLE `student_answers` (
    `answer_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `question_id` BIGINT NOT NULL,
    `exam_id` BIGINT COMMENT '시험지 답안일 때 (exams.exam_id)',
    `student_answer` VARCHAR(500),
    `is_correct` BOOLEAN,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE,
    FOREIGN KEY (`question_id`) REFERENCES `questions`(`question_id`) ON DELETE CASCADE,
    FOREIGN KEY (`exam_id`) REFERENCES `exams`(`exam_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. 시험지 점수 (StudentScore)
--     학생 검증 페이지에서 [전체 확정] 시 1 row INSERT.
--     시험 메타는 exams 테이블 JOIN으로 조회.
-- ============================================================
CREATE TABLE `student_scores` (
    `score_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `exam_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL,
    `total_questions` INT NOT NULL,
    `correct_count` INT NOT NULL,
    `total_points` INT COMMENT '배점 합계 (questions.point 합산). NULL이면 구 데이터 — 정답률(correct_count/total_questions)로 표시.',
    `earned_points` INT COMMENT '맞은 문항의 배점 합계. total_points와 함께 NULL or 함께 채워짐.',
    `confirmed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE,
    FOREIGN KEY (`exam_id`) REFERENCES `exams`(`exam_id`) ON DELETE CASCADE,
    FOREIGN KEY (`source_id`) REFERENCES `sources`(`source_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
