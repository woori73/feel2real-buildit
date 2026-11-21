-- 재원생 디지털 모의고사 앱(/naesintest/app) — 2026-11-21 홈페이지 개편 지시서 6번
-- 원장이 학생부 학생에게 접속 코드를 발급하고(재발급·폐기 가능), 시험을 배정한다.
-- 학생이 앱에서 시험을 끝내면 응시 기록이 바로 남고(학생부 채점 기록에도 source='APP'으로 한 줄),
-- 원장과 원장이 지정한 사람만 '새 응시 기록'을 본다. 학생이 그만두어도 응시 기록은 지우지 않는다.

-- 1) 접속 코드: 코드 원문은 저장하지 않고 SHA-256만 둔다. 학생마다 ACTIVE는 하나(재발급하면 이전 코드는 REVOKED).
CREATE TABLE `student_app_codes` (
    `code_id`      BIGINT      NOT NULL AUTO_INCREMENT,
    `business_id`  BIGINT      NOT NULL,
    `student_id`   BIGINT      NOT NULL,
    `code_hash`    CHAR(64)    NOT NULL COMMENT 'SHA-256(대문자·하이픈 없는 코드)',
    `code_tail`    VARCHAR(4)  NOT NULL COMMENT '화면 표시용 끝 2글자',
    `status`       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
    `issued_by`    BIGINT      NULL,
    `issued_at`    DATETIME    NOT NULL,
    `revoked_at`   DATETIME    NULL,
    `last_used_at` DATETIME    NULL,
    PRIMARY KEY (`code_id`),
    UNIQUE KEY `uk_student_app_codes_hash` (`code_hash`),
    INDEX `idx_student_app_codes_student` (`business_id`, `student_id`, `status`),
    CONSTRAINT `fk_student_app_codes_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_student_app_codes_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 시험 배정: 원장이 고른 내신 디지털 모의고사(tp_exams.exam_code). 빼면 removed_at만 채운다(다시 넣으면 비운다).
CREATE TABLE `student_exam_assignments` (
    `assignment_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `business_id`   BIGINT      NOT NULL,
    `student_id`    BIGINT      NOT NULL,
    `exam_code`     VARCHAR(64) NOT NULL,
    `assigned_by`   BIGINT      NULL,
    `assigned_at`   DATETIME    NOT NULL,
    `removed_at`    DATETIME    NULL,
    PRIMARY KEY (`assignment_id`),
    UNIQUE KEY `uk_student_exam_assignments` (`business_id`, `student_id`, `exam_code`),
    CONSTRAINT `fk_student_exam_assignments_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_student_exam_assignments_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 응시 기록: 제출 한 번에 한 줄(attempt_id 유일). 같은 회차를 다시 치면 attempt_no가 늘어난다. 삭제 기능 없음.
CREATE TABLE `student_exam_results` (
    `result_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `business_id`   BIGINT       NOT NULL,
    `student_id`    BIGINT       NOT NULL,
    `attempt_id`    BIGINT       NOT NULL,
    `grading_id`    BIGINT       NULL COMMENT '학생부 채점 기록(easy_grading_records, source=APP)',
    `exam_code`     VARCHAR(64)  NOT NULL,
    `exam_title`    VARCHAR(200) NOT NULL,
    `attempt_no`    INT          NOT NULL,
    `started_at`    DATETIME     NOT NULL,
    `submitted_at`  DATETIME     NOT NULL,
    `duration_sec`  INT          NOT NULL,
    `score`         INT          NULL,
    `max_score`     INT          NULL,
    `total_count`   INT          NOT NULL,
    `correct_count` INT          NOT NULL,
    `wrong_items`   TEXT         NULL COMMENT 'JSON [{"no":"3","topic":"관계대명사","reason":"…"}]',
    `checked_by`    BIGINT       NULL,
    `checked_at`    DATETIME     NULL COMMENT '원장(또는 지정한 사람)이 점검한 때. NULL이면 새 응시 기록',
    `created_at`    DATETIME     NOT NULL,
    PRIMARY KEY (`result_id`),
    UNIQUE KEY `uk_student_exam_results_attempt` (`attempt_id`),
    INDEX `idx_student_exam_results_new` (`business_id`, `checked_at`, `submitted_at`),
    INDEX `idx_student_exam_results_student` (`student_id`, `exam_code`, `submitted_at`),
    CONSTRAINT `fk_student_exam_results_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_student_exam_results_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`),
    CONSTRAINT `fk_student_exam_results_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `tp_attempts` (`attempt_id`),
    CONSTRAINT `fk_student_exam_results_grading` FOREIGN KEY (`grading_id`) REFERENCES `easy_grading_records` (`grading_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 응시 기록을 볼 수 있는 사람: 원장(ADMIN)은 항상, 그 밖에는 원장이 지정한 계정만. 해제하면 revoked_at만 채운다.
CREATE TABLE `student_exam_viewers` (
    `viewer_id`   BIGINT   NOT NULL AUTO_INCREMENT,
    `business_id` BIGINT   NOT NULL,
    `user_id`     BIGINT   NOT NULL,
    `granted_by`  BIGINT   NULL,
    `granted_at`  DATETIME NOT NULL,
    `revoked_at`  DATETIME NULL,
    PRIMARY KEY (`viewer_id`),
    UNIQUE KEY `uk_student_exam_viewers` (`business_id`, `user_id`),
    CONSTRAINT `fk_student_exam_viewers_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_student_exam_viewers_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
