-- ============================================================
-- V1017: NewStart 감사회원 최초 1회 본인확인 요청(thanks claims) + roster 1:1 연결 제약
--   · 목적: 현재 네이버 OAuth 회원(users / social_accounts.provider_user_id)과 사전자격명부
--     (newstart_thanks_roster 503명)를 "운영자 확인을 거쳐" 1:1 로 연결하고 THANKS_MEMBER 를 부여한다.
--   · naver_id_prefix(V1016)나 닉네임만으로 자동 부여하지 않는다. 같은 접두부·공개 닉네임을 가진 일반회원이
--     오인증되지 않도록 요청(claim)은 PENDING 으로만 생기고, 부여는 ADMIN 승인 한 곳에서만 일어난다.
--   · 기존 V1015(roster 503) / V1016(naver_id_prefix) 데이터는 수정하지 않는다.
--     roster 에는 linked_user_id / linked_provider_user_id 를 1:1 로 강제하는 UNIQUE 만 추가한다.
--     (현재 두 컬럼은 전부 NULL 이므로 UNIQUE 추가가 기존 행에 영향을 주지 않는다 — MySQL UNIQUE 는 NULL 중복 허용)
--   · newstart_member_grants(실제 권한)는 건드리지 않는다. 승인 시 기존 NewStartGrantService 경로로 row 를 넣는다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `newstart_thanks_roster`
    ADD UNIQUE KEY `uk_newstart_thanks_roster_linked_user` (`linked_user_id`),
    ADD UNIQUE KEY `uk_newstart_thanks_roster_linked_provider` (`linked_provider_user_id`);

CREATE TABLE `newstart_thanks_claims` (
    `claim_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '요청한 users.user_id (네이버 로그인 회원)',
    `roster_id` BIGINT NOT NULL COMMENT '본인이라고 주장하는 newstart_thanks_roster.roster_id',
    `provider_user_id` VARCHAR(64) NOT NULL COMMENT '요청 시점 네이버 OAuth subject(response.id) 스냅샷 — 승인 시 재확인',
    `verify_code` VARCHAR(16) NOT NULL COMMENT '카페 쪽지/댓글로 운영자에게 보내는 1회용 인증코드',
    `status` VARCHAR(12) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED / CANCELLED',
    `email_prefix_match` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '네이버 이메일 로컬파트가 roster 접두부로 시작하는지(보조 증거, 단독 승인 근거 아님)',
    `decided_by` VARCHAR(50) NULL COMMENT '승인/반려 운영자 표식',
    `decided_at` DATETIME NULL,
    `reject_reason` VARCHAR(200) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) NULL,
    `updated_by` VARCHAR(50) NULL,
    PRIMARY KEY (`claim_id`),
    UNIQUE KEY `uk_newstart_thanks_claim_user_roster` (`user_id`, `roster_id`),
    KEY `idx_newstart_thanks_claim_status` (`status`),
    KEY `idx_newstart_thanks_claim_roster` (`roster_id`),
    CONSTRAINT `fk_newstart_thanks_claim_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_newstart_thanks_claim_roster` FOREIGN KEY (`roster_id`) REFERENCES `newstart_thanks_roster` (`roster_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
