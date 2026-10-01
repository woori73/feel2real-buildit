-- ============================================================
-- V1017: NewStart 접속상태 + 내부 쪽지 (1:1 DM)
--   · newstart_member_presence: user_id PK(FK→users CASCADE),
--     last_seen_at(heartbeat 최종 시각), hide_online(접속상태 숨김),
--     dm_blocked(쪽지 수신 거부). 행은 첫 ping 시 생성된다.
--   · newstart_direct_messages: 1:1 내부 쪽지. 송수신자는
--     users.user_id로만 저장한다(닉네임 문자열·loginId 저장 금지 —
--     표시명은 NewStartMemberProfileService로 조회 시 resolve).
--   · 신규 테이블만 추가한다. 기존 행·GRANT/OAuth 로직 변경 없음.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_member_presence` (
    `user_id` BIGINT NOT NULL COMMENT 'PK = users.user_id (canonical identity)',
    `last_seen_at` TIMESTAMP NULL DEFAULT NULL COMMENT 'heartbeat 최종 수신 시각',
    `hide_online` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '접속상태 숨김',
    `dm_blocked` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '쪽지 수신 거부',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_newstart_presence_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    INDEX `idx_newstart_presence_seen` (`last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `newstart_direct_messages` (
    `message_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sender_user_id` BIGINT NOT NULL COMMENT '발신 = users.user_id (서버 principal에서만 결정)',
    `receiver_user_id` BIGINT NOT NULL COMMENT '수신 = users.user_id',
    `message` VARCHAR(1000) NOT NULL COMMENT '쪽지 본문',
    `is_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '수신자 읽음 표시',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_newstart_dm_sender` FOREIGN KEY (`sender_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_newstart_dm_receiver` FOREIGN KEY (`receiver_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    INDEX `idx_newstart_dm_inbox` (`receiver_user_id`, `message_id`),
    INDEX `idx_newstart_dm_unread` (`receiver_user_id`, `is_read`, `message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
