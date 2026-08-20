-- ============================================================
-- V11: 숙제 검토(reviewed) + 강사 피드백(feedback) 스키마 (US-002 / US-003)
--   1. learning_materials 에 강사 검토 상태 컬럼 추가 (reviewed / reviewed_at / reviewed_by)
--      · DEFAULT FALSE 가 있어 ADD COLUMN NOT NULL 1-step 안전 (CLAUDE.md 3단계 룰 비대상)
--      · reviewed_by FK 는 nullable → 무결성 위반 없음 (additive)
--   2. sources.source_type ENUM 끝에 HOMEWORK_FEEDBACK 추가
--      · 강사가 학생에게 돌려주는 채점/피드백 PDF 용 값.
--      · ENUM 끝에 값만 추가 → 기존 row 의 source_type 값 변화 없음 (V10 문법 미러).
--   3. learning_material_feedbacks 신규 테이블 (learning_material_answers 미러)
--      · 한 material 에 여러 피드백 source 매핑 (order_num 1부터).
-- 모두 additive 라 기존 데이터와 무관 → 단일 V11 파일.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ------------------------------------------------------------
-- 1. learning_materials 강사 검토 컬럼
-- ------------------------------------------------------------
ALTER TABLE `learning_materials`
    ADD COLUMN `reviewed` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN `reviewed_at` DATETIME NULL,
    ADD COLUMN `reviewed_by` BIGINT NULL,
    ADD CONSTRAINT `fk_learning_materials_reviewed_by`
        FOREIGN KEY (`reviewed_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL;

-- ------------------------------------------------------------
-- 2. sources.source_type ENUM 에 HOMEWORK_FEEDBACK 추가
-- ------------------------------------------------------------
ALTER TABLE `sources`
    MODIFY COLUMN `source_type`
        ENUM('STUDENT_MATERIAL', 'HOMEWORK_PROBLEM', 'HOMEWORK_ANSWER', 'TUTOR_MATERIAL', 'HOMEWORK_FEEDBACK') NOT NULL;

-- ------------------------------------------------------------
-- 3. 강사 피드백 결과물 매핑 (learning_material_answers 미러)
--   · source_type=HOMEWORK_FEEDBACK 인 source 들을 material 에 연결
--   · order_num: 피드백 업로드 순서 (1부터)
-- ------------------------------------------------------------
CREATE TABLE `learning_material_feedbacks` (
    `feedback_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `material_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL COMMENT 'sources.source_type=HOMEWORK_FEEDBACK',
    `order_num` INT NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_learning_material_feedbacks_material` FOREIGN KEY (`material_id`) REFERENCES `learning_materials`(`material_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_learning_material_feedbacks_source` FOREIGN KEY (`source_id`) REFERENCES `sources`(`source_id`) ON DELETE CASCADE,
    KEY `idx_learning_material_feedbacks_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
