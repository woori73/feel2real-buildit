-- ============================================================
-- V4: tutor_student_map 부활
--   · Phase 2(§5.5 학습자료 부여)에서 TUTOR가 학생 conversation/자료에
--     접근할 때의 권한 가드 기반.
--   · 예전 PRD의 tutor_student_map과 차이:
--       - tutor_id → tutor_user_id (academies.tutor_user_id와 명명 통일)
--       - subject_id 제외 (MVP는 영어 단일)
--       - UNIQUE (tutor_user_id, student_id)
--       - created_by/updated_by 타입 VARCHAR(50) — BaseEntity는 loginId 사용
--   · V3은 Codex 평행 작업(academy/lesson)으로 예약되어 비워둠.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `tutor_student_map` (
    `mapping_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tutor_user_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_tutor_student_map_tutor` FOREIGN KEY (`tutor_user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tutor_student_map_student` FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_tutor_student_pair` (`tutor_user_id`, `student_id`),
    KEY `idx_tutor_student_map_tutor` (`tutor_user_id`, `is_active`),
    KEY `idx_tutor_student_map_student` (`student_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
