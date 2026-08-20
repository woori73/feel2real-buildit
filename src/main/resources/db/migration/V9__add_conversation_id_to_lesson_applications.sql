-- ============================================================
-- V9: lesson_applications.conversation_id (nullable FK)
--   · 수업 신청 ↔ 학습 대화 매칭. nullable 이므로 기존 row backfill 불필요 (1-step).
--   · 신규 nullable 컬럼이라 모든 기존 row가 NULL → FK 위반 불가능.
--     (CLAUDE.md §3단계 룰은 기존 row가 참조 무결성을 깰 때 적용;
--      여기서는 conversation_id가 신규 컬럼 전부 NULL → 1-step 안전)
--   · ON DELETE SET NULL: conversation 삭제 시 신청 row는 유지, FK만 NULL 처리.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `lesson_applications`
    ADD COLUMN `conversation_id` BIGINT NULL AFTER `student_id`,
    ADD KEY `idx_lesson_applications_conversation` (`conversation_id`),
    ADD CONSTRAINT `fk_lesson_applications_conversation`
        FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`conversation_id`)
        ON DELETE SET NULL;
