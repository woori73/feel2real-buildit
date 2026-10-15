-- 비밀번호 재설정 토큰: 원문 토큰은 저장하지 않고 SHA-256 해시만 저장한다.
CREATE TABLE `password_reset_tokens` (
    `password_reset_token_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `token_hash` VARCHAR(64) NOT NULL UNIQUE,
    `expires_at` TIMESTAMP NOT NULL,
    `used_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_password_reset_tokens_user_id` (`user_id`),
    INDEX `idx_password_reset_tokens_token_hash` (`token_hash`),
    INDEX `idx_password_reset_tokens_expires_at` (`expires_at`),
    CONSTRAINT `fk_password_reset_tokens_user`
        FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
