-- ============================================================
-- V1021: NewStart ADMIN 비밀번호 초기화 + 회원 본인 변경
--   · newstart_admin_password_resets: ADMIN이 회원 비밀번호를 1회용
--     임시 비밀번호로 초기화한 사실만 남기는 append-only 감사 로그.
--     target_user_id(대상 회원) / admin_user_id·admin_login_id(실행 ADMIN) /
--     created_at(실행 시각) 뿐 — 비밀번호 원문·hash·임시 값은 어떤 컬럼에도 저장하지 않는다.
--   · users.password_reset_required: ADMIN 초기화 직후 TRUE. 회원이 새 비밀번호를
--     설정하면 FALSE. 기본값 FALSE라 기존 행은 아무것도 요구되지 않는다(재가입·데이터 변경 없음).
--   · users.last_login_at: 로그인 성공 시각. NULL = 이 컬럼 생성 이후 로그인 기록 없음
--     (과거 로그인 이력은 어디에도 남아있지 않아 소급 생성하지 않는다).
--   · 신규 컬럼은 모두 nullable/default — 기존 row를 건드리는 backfill·제약 강제가 없어 1단계로 끝낸다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `users`
    ADD COLUMN `password_reset_required` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'ADMIN이 임시 비밀번호로 초기화함 — 회원이 새 비밀번호를 설정하면 FALSE',
    ADD COLUMN `last_login_at` TIMESTAMP NULL DEFAULT NULL COMMENT '최근 로그인 성공 시각(이 컬럼 도입 이후만)';

CREATE TABLE `newstart_admin_password_resets` (
    `reset_id`        BIGINT NOT NULL AUTO_INCREMENT,
    `target_user_id`  BIGINT NOT NULL COMMENT '초기화된 회원 = users.user_id',
    `target_login_id` VARCHAR(50) NOT NULL COMMENT '실행 시점 대상 로그인 ID 스냅샷',
    `admin_user_id`   BIGINT NOT NULL COMMENT '실행 ADMIN users.user_id',
    `admin_login_id`  VARCHAR(50) NOT NULL COMMENT '실행 ADMIN 로그인 ID 스냅샷',
    `created_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`reset_id`),
    INDEX `idx_newstart_admin_pwd_reset_target` (`target_user_id`, `created_at`),
    CONSTRAINT `fk_newstart_admin_pwd_reset_target` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
