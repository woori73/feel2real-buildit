-- ============================================================
-- V981: NewStart 회원 채팅 (공용방 1개, 텍스트 전용)
--   · newstart_chat_messages: 회원 ↔ 회원 실시간 채팅 메시지
--     (NewStart AI 질문(/api/newstart/ai/chat)과 별개 — AI 학습/RAG/embedding
--      에 사용하지 않는 회원 대화 데이터)
--   · 작성자는 users.user_id FK로만 확정 — 브라우저 전송값으로 결정하지 않음
--   · 삭제는 soft delete(deleted=true, 본문 비움) — pagination id 안정성 유지
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_chat_messages` (
    `message_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '작성자 users.user_id (서버 principal에서만 결정)',
    `message` VARCHAR(500) NOT NULL COMMENT '메시지 본문 (삭제 시 빈 문자열)',
    `deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'soft delete — 본인(또는 ADMIN) 삭제',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_newstart_chat_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    INDEX `idx_newstart_chat_recent` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
