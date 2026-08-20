-- ============================================================
-- V6: Lesson applications
--   · 학생이 공개 수업 상품에 신청하면 즉시 APPROVED 처리
--   · 강사 승인/거절 플로우는 MVP 제외
--   · student_id + lesson_id 단일 row로 중복 신청을 방지
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `lesson_applications` (
    `application_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `lesson_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `status` ENUM('APPROVED', 'CANCELED') NOT NULL,
    `applied_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `canceled_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_lesson_applications_student_lesson` (`student_id`, `lesson_id`),
    KEY `idx_lesson_applications_student_status` (`student_id`, `status`),
    KEY `idx_lesson_applications_lesson_status` (`lesson_id`, `status`),
    CONSTRAINT `fk_lesson_applications_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lesson_products`(`lesson_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_lesson_applications_student` FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
