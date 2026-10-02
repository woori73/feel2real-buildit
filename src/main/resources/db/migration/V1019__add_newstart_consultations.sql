-- ============================================================
-- V1019: NewStart 감사회원 1:1 비공개 상담 스레드
--   · newstart_consultations: 상담 "건"(스레드). 회원 1명당 여러 건.
--     member_user_id만 저장(닉네임/loginId 저장 금지 — 표시명은 조회 시
--     NewStartMemberProfileService로 resolve). link_debt=회원이 본인 채무상황
--     연결을 허용했는지. atoz_step=생성 시점 결정적 A to Z 단계(없으면 NULL).
--     member/operator_last_seen_message_id=읽음 커서("새 답변 N건" 배지용).
--   · newstart_consultation_messages: 스레드 메시지. author_role=MEMBER|OPERATOR|
--     AI_RESEARCH. AI_RESEARCH는 운영자 전용(회원 조회에서 항상 제외)이며
--     회원에게 자동 전송되지 않는다. content는 TEXT(상담 본문).
--   · 기존 쪽지(newstart_direct_messages)·채팅·채무상황·지피지기 DB는 건드리지
--     않는다. 신규 테이블만 추가(기존 row 무관 → 1단계).
--   · 접근 권한: 회원 본인 + ADMIN. API/DB에서 강제(HARD GATE).
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_consultations` (
    `consultation_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `member_user_id` BIGINT NOT NULL COMMENT '상담 소유 회원 = users.user_id (서버 principal에서만 결정)',
    `status` VARCHAR(30) NOT NULL COMMENT 'NEW|AWAITING_ANSWER|OPERATOR_ANSWERED|MEMBER_FOLLOWUP',
    `link_debt` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '회원이 본인 채무상황 연결을 허용했는지',
    `atoz_step` INT NULL COMMENT '생성 시점 결정적 A to Z 단계(0..13), 신호 없으면 NULL',
    `member_last_seen_message_id` BIGINT NULL COMMENT '회원 읽음 커서(새 답변 배지용)',
    `operator_last_seen_message_id` BIGINT NULL COMMENT '운영자 읽음 커서',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_message_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '정렬용 마지막 메시지 시각',
    CONSTRAINT `fk_newstart_consult_member` FOREIGN KEY (`member_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    INDEX `idx_newstart_consult_member` (`member_user_id`, `consultation_id`),
    INDEX `idx_newstart_consult_status` (`status`, `last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `newstart_consultation_messages` (
    `message_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `consultation_id` BIGINT NOT NULL COMMENT 'FK → newstart_consultations',
    `author_role` VARCHAR(20) NOT NULL COMMENT 'MEMBER|OPERATOR|AI_RESEARCH (AI_RESEARCH는 운영자 전용)',
    `content` TEXT NOT NULL COMMENT '메시지 본문',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_newstart_consult_msg` FOREIGN KEY (`consultation_id`) REFERENCES `newstart_consultations` (`consultation_id`) ON DELETE CASCADE,
    INDEX `idx_newstart_consult_msg_thread` (`consultation_id`, `message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
