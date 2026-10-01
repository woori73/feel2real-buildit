-- ============================================================
-- V1017: NewStart 감사회원 최초 1회 셀프 인증(자동 1:1 연결) + 인증 예외함 + roster 1:1 제약
--   · 목적: 감사회원 대부분이 운영자 승인 없이 스스로 인증한다. 회원은 카페 닉네임을 1회만 입력하고, 서버가
--     (닉네임 정확 일치 + 네이버 OAuth 이메일(@naver.com) 로컬파트 == roster 접두부 + roster/OAuth 계정 모두 미연결 +
--     후보 정확히 1명)을 모두 확인한 경우에만 roster ↔ OAuth userId 를 1:1 로 연결하고 THANKS_MEMBER 를 부여한다.
--   · prefix 단독 / 닉네임 단독 인증은 없다. 자동으로 안전하게 끝낼 수 없는 경우(이메일 없음·@naver.com 아님·중복·충돌·반복 실패 등)만
--     "예외"로 적재하고 운영자에게 알린다.
--   · 기존 V1015(roster 503) / V1016(naver_id_prefix) 데이터·컬럼은 수정하지 않는다. roster 에는 linked_user_id /
--     linked_provider_user_id 를 1:1 로 강제하는 UNIQUE 만 추가한다(현재 전부 NULL → 기존 행 영향 없음, MySQL UNIQUE 는 NULL 다중 허용).
--   · user_social_accounts.provider_email: 로그인 때마다 OAuth 가 준 이메일을 보관한다(users.email 은 최초 가입 때만 채워져
--     이메일 동의 이전 가입자·이메일 변경자는 영구히 비어 있었다). 인증 판정에만 쓰고 화면/메일에 노출하지 않는다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `user_social_accounts`
    ADD COLUMN `provider_email` VARCHAR(100) NULL COMMENT '최근 로그인에서 OAuth 가 제공한 이메일(소문자 정규화). 감사회원 셀프 인증 판정 전용' AFTER `provider_user_id`;

ALTER TABLE `newstart_thanks_roster`
    ADD UNIQUE KEY `uk_newstart_thanks_roster_linked_user` (`linked_user_id`),
    ADD UNIQUE KEY `uk_newstart_thanks_roster_linked_provider` (`linked_provider_user_id`);

CREATE TABLE `newstart_thanks_verifications` (
    `verification_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '인증을 시도한 users.user_id (네이버 로그인 회원)',
    `roster_id` BIGINT NULL COMMENT '대상 newstart_thanks_roster.roster_id (후보를 특정하지 못한 시도는 NULL)',
    `nickname_input` VARCHAR(50) NOT NULL COMMENT '회원이 입력한 카페 닉네임(정규화)',
    `provider_user_id` VARCHAR(64) NOT NULL COMMENT '시도 시점 네이버 OAuth subject(response.id) 스냅샷 — 운영자 처리 시 재확인',
    `outcome` VARCHAR(16) NOT NULL COMMENT 'AUTO_APPROVED / FAILED / EXCEPTION',
    `exception_reason` VARCHAR(32) NULL COMMENT 'EXCEPTION 사유 코드',
    `status` VARCHAR(12) NOT NULL DEFAULT 'DONE' COMMENT 'DONE(자동 처리 끝) / OPEN(운영자 처리 대기) / RESOLVED(연결 승인) / DISMISSED(종료)',
    `email_status` VARCHAR(16) NULL COMMENT '운영자 알림 메일: SENT / FAILED / NOT_CONFIGURED / SKIPPED(중복 억제)',
    `email_sent_at` DATETIME NULL,
    `resolved_by` VARCHAR(50) NULL,
    `resolved_at` DATETIME NULL,
    `resolution_note` VARCHAR(200) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) NULL,
    `updated_by` VARCHAR(50) NULL,
    PRIMARY KEY (`verification_id`),
    KEY `idx_newstart_thanks_ver_user` (`user_id`, `outcome`, `created_at`),
    KEY `idx_newstart_thanks_ver_status` (`status`, `outcome`),
    KEY `idx_newstart_thanks_ver_roster` (`roster_id`),
    CONSTRAINT `fk_newstart_thanks_ver_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_newstart_thanks_ver_roster` FOREIGN KEY (`roster_id`) REFERENCES `newstart_thanks_roster` (`roster_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 신규 감사회원 사전등록(카페 roster 503명에 없는 회원). 이메일은 운영자가 ADMIN API 로 등록한다(소스 하드코딩 금지).
-- 인증은 사용자가 이메일을 입력하는 방식이 아니라, Naver OAuth 가 실제 반환한 이메일(user_social_accounts.provider_email)이
-- 등록된 이메일과 정확히 일치할 때만 한다. 일치하면 OAuth userId 와 1:1 연결 + THANKS_MEMBER 부여.
CREATE TABLE `newstart_thanks_preregistrations` (
    `prereg_id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(100) NOT NULL COMMENT '사전등록 네이버 이메일(소문자 정규화, @naver.com). 공개 화면 노출 금지',
    `memo` VARCHAR(100) NULL COMMENT '운영자 메모',
    `linked_user_id` BIGINT NULL COMMENT '연결된 users.user_id',
    `linked_provider_user_id` VARCHAR(64) NULL COMMENT '연결 시점 네이버 OAuth subject',
    `linked_at` DATETIME NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) NULL,
    `updated_by` VARCHAR(50) NULL,
    PRIMARY KEY (`prereg_id`),
    UNIQUE KEY `uk_newstart_thanks_prereg_email` (`email`),
    UNIQUE KEY `uk_newstart_thanks_prereg_linked_user` (`linked_user_id`),
    UNIQUE KEY `uk_newstart_thanks_prereg_linked_provider` (`linked_provider_user_id`),
    CONSTRAINT `fk_newstart_thanks_prereg_user` FOREIGN KEY (`linked_user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
