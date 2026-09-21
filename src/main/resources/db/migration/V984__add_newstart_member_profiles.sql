-- ============================================================
-- V984: NewStart 전용 표시 별명(display nickname)
--   · newstart_member_profiles: users(공용 identity)와 분리된 NewStart 전용 표시 데이터
--     users.nickname은 BuildIt/Wiki/Academy 등 다른 서비스가 공유하는 전역 필드라
--     NewStart 회원이 여기서 별명을 바꾸면 그 서비스 화면까지 함께 바뀐다 — 그래서
--     NewStart는 자기 표시 이름을 따로 둔다.
--   · user_id = users.user_id 그대로 PK(1:1) — 새 식별자를 만들지 않는다.
--   · display_nickname에는 unique 제약을 두지 않는다(§3 조사: 기존 users.nickname도
--     중복 허용 정책 — 별명은 인증/식별 key가 아니라 표시 문자열일 뿐).
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_member_profiles` (
    `user_id` BIGINT NOT NULL COMMENT 'users.user_id (1:1, 새 식별자 아님)',
    `display_nickname` VARCHAR(20) NOT NULL COMMENT 'NewStart 전용 표시 별명(실명 아님)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_newstart_member_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
