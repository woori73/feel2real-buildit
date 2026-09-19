-- ============================================================
-- V982: 소셜 로그인 계정 연결 (네이버 OAuth 로그인)
--   · user_social_accounts: provider + provider_user_id(stable identity) ↔ users.user_id
--   · 회원 식별은 이 연결 레코드로만 수행 — 이메일 자동 병합 정책 아님(계정 오병합 방지)
--   · users 테이블/기존 ID/PW 로그인/ADMIN 권한 체계는 전혀 변경하지 않는다.
--   · (provider, provider_user_id) unique = 동일 소셜 계정의 중복 회원 생성 원천 차단
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `user_social_accounts` (
    `social_account_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT 'Feel2Real users.user_id',
    `provider` VARCHAR(20) NOT NULL COMMENT 'NAVER 등 (SocialProvider enum)',
    `provider_user_id` VARCHAR(64) NOT NULL COMMENT 'provider의 stable user id (네이버 result.id)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_social_account_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    UNIQUE KEY `uq_social_provider_identity` (`provider`, `provider_user_id`),
    UNIQUE KEY `uq_social_user_provider` (`user_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
