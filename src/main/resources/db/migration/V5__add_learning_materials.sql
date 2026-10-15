-- ============================================================
-- V5: 학습자료 부여 + 풀이 재업로드 (Phase 2 §5.5)
--   · Admin 또는 TUTOR가 학생에게 PDF 학습자료를 부여
--   · 학생이 PDF 다운로드 → 종이로 풀이 → 사진/PDF로 재업로드
--   · 재업로드 = 기존 결과물 대치 (learning_material_answers는 row 전부 삭제 후 재INSERT)
--   · Admin 채점/피드백 PDF 흐름은 §7 후속 — 이번 미구현
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 학습자료 (LearningMaterial)
--   · 한 conversation 안에서 부여 (assigned_by user_id — role 무관)
--   · status: ASSIGNED → 풀이 업로드 시 COMPLETED
-- ============================================================
CREATE TABLE `learning_materials` (
    `material_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `conversation_id` BIGINT NOT NULL,
    `problem_source_id` BIGINT NOT NULL COMMENT 'sources.source_type=HOMEWORK_PROBLEM',
    `assigned_by` BIGINT NOT NULL COMMENT 'user_id (Admin 또는 TUTOR). role 무관.',
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT,
    `status` ENUM('ASSIGNED', 'COMPLETED') NOT NULL DEFAULT 'ASSIGNED',
    `assigned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_learning_materials_student` FOREIGN KEY (`student_id`) REFERENCES `students`(`student_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_learning_materials_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`conversation_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_learning_materials_problem_source` FOREIGN KEY (`problem_source_id`) REFERENCES `sources`(`source_id`),
    CONSTRAINT `fk_learning_materials_assigned_by` FOREIGN KEY (`assigned_by`) REFERENCES `users`(`user_id`),
    KEY `idx_learning_materials_student_status` (`student_id`, `status`),
    KEY `idx_learning_materials_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 학생 풀이 결과물 매핑 (LearningMaterialAnswer)
--   · 재업로드 시 기존 row 전부 삭제 → 새 source_id로 재INSERT
--   · order_num: 학생이 업로드한 순서 (1부터)
-- ============================================================
CREATE TABLE `learning_material_answers` (
    `answer_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `material_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL COMMENT 'sources.source_type=HOMEWORK_ANSWER',
    `order_num` INT NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_learning_material_answers_material` FOREIGN KEY (`material_id`) REFERENCES `learning_materials`(`material_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_learning_material_answers_source` FOREIGN KEY (`source_id`) REFERENCES `sources`(`source_id`),
    KEY `idx_learning_material_answers_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
