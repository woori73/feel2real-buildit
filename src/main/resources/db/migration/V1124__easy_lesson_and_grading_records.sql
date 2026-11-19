-- 학생부(2026-11-19, 관리자 전용): 수업 기록·채점 기록·채점 사진(문제지·정답지·학생 답안)·약점 정리. 추가만 하고 기존 테이블은
-- 바꾸지 않는다. 말 명령(AI 코디네이터) 또는 관리자 화면에서 기록한다. 수정·삭제 가능(사용자 결정). 사진은 JPEG로 다시 저장해
-- (메타데이터 제거) FileStorage(로컬/S3)에 두고 여기에는 저장 키만 둔다. 채점 기록을 지우면 사진 파일도 함께 지운다.
-- 채점은 F2R 서버가 하지 않는다: 사진을 먼저 올리면(PENDING) 휴대폰 Claude가 MCP로 사진을 읽어 채점하고 결과를 저장한다(GRADED).

CREATE TABLE `easy_lesson_records` (
    `lesson_record_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `class_id` BIGINT NULL,
    `lesson_date` DATE NOT NULL,
    `lesson_range` VARCHAR(300) NULL COMMENT '수업 범위(교재·단원·쪽)',
    `homework_check` VARCHAR(20) NULL COMMENT '지난 숙제 확인: DONE/PARTIAL/NOT_DONE (NULL=확인 안 함)',
    `homework_done_note` VARCHAR(500) NULL COMMENT '해 온 숙제·과제 내용',
    `next_homework` VARCHAR(500) NULL COMMENT '이번에 낸 숙제',
    `teacher_note` VARCHAR(1000) NULL COMMENT '수업 메모(태도·이해도 등)',
    `source` VARCHAR(10) NOT NULL DEFAULT 'WEB' COMMENT 'WEB/MCP',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_lesson_records_student_day` (`student_id`, `lesson_date`),
    INDEX `idx_easy_lesson_records_business_day` (`business_id`, `lesson_date`),
    CONSTRAINT `fk_easy_lesson_records_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_lesson_records_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`),
    CONSTRAINT `fk_easy_lesson_records_class` FOREIGN KEY (`class_id`) REFERENCES `easy_classes` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_grading_records` (
    `grading_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `lesson_record_id` BIGINT NULL COMMENT '같은 날 수업 기록(있으면)',
    `graded_on` DATE NOT NULL,
    `subject_area` VARCHAR(100) NULL COMMENT '분야(문법·독해·어휘 등)',
    `material` VARCHAR(200) NULL COMMENT '교재·과제 이름·범위',
    `total_count` INT NULL,
    `correct_count` INT NULL,
    `wrong_items` TEXT NULL COMMENT 'JSON 배열 [{no, topic, reason}] — 오답 기록',
    `feedback` VARCHAR(2000) NULL,
    `status` VARCHAR(10) NOT NULL DEFAULT 'GRADED' COMMENT 'PENDING(사진만 올림, 채점 전)/GRADED',
    `source` VARCHAR(10) NOT NULL DEFAULT 'WEB' COMMENT 'WEB/MCP',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_grading_student_day` (`student_id`, `graded_on`),
    INDEX `idx_easy_grading_business_status` (`business_id`, `status`),
    CONSTRAINT `fk_easy_grading_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_grading_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`),
    CONSTRAINT `fk_easy_grading_lesson` FOREIGN KEY (`lesson_record_id`) REFERENCES `easy_lesson_records` (`lesson_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_grading_photos` (
    `photo_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `grading_id` BIGINT NOT NULL,
    `kind` VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT 'QUESTION(문제지)/ANSWER_KEY(정답지)/STUDENT(학생 답안)/GRADED(채점된 답지)',
    `storage_key` VARCHAR(500) NOT NULL,
    `content_type` VARCHAR(40) NOT NULL,
    `size_bytes` BIGINT NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_grading_photos_grading` (`grading_id`),
    CONSTRAINT `fk_easy_grading_photos_grading` FOREIGN KEY (`grading_id`) REFERENCES `easy_grading_records` (`grading_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 약점 정리: Claude(또는 원장)가 채점·수업 기록을 보고 쓴 학생별 약점 요약. 쌓아 두고 최신 것을 현재 정리로 보여 준다.
CREATE TABLE `easy_student_weakness_notes` (
    `note_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `summary` VARCHAR(3000) NOT NULL,
    `topics` TEXT NULL COMMENT 'JSON 배열 [{topic, level(HIGH/MEDIUM/LOW), evidence}]',
    `source` VARCHAR(10) NOT NULL DEFAULT 'WEB' COMMENT 'WEB/MCP',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_weakness_student` (`student_id`, `note_id`),
    CONSTRAINT `fk_easy_weakness_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_weakness_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
