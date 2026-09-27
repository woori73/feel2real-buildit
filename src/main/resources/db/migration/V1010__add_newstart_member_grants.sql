-- ============================================================
-- V1010: NewStart 회원 전용 실행 권한(grant)
--   · newstart_member_grants: capability 1개 = row 1개. row가 존재하면 부여, DELETE 하면 회수.
--     users.role(STUDENT/ADMIN/TUTOR/CREATOR)은 BuildIt/Wiki/Academy가 공유하는 전역 권한이라
--     NewStart 실행 권한을 거기에 섞지 않는다 — 다른 서비스 권한과 상호 오염되기 때문.
--   · UNIQUE(user_id, capability): 같은 capability 중복 부여를 DB에서 차단한다
--     (취소 이력 컬럼을 두면 NULL 중복이 허용되어 부여 여부 판정이 애매해진다 — 물리 행이 곧 상태다).
--   · capability는 VARCHAR 저장: 새 capability 추가는 enum 값 확장만으로 가능(migration 불필요).
--   · 이 파일은 스키마만 만든다. seed/부여 INSERT는 운영에서 수동 수행(자동 실행 금지).
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_member_grants` (
    `grant_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT 'users.user_id',
    `capability` VARCHAR(40) NOT NULL COMMENT 'THANKS_MEMBER 등 NewStart 실행 capability 식별자',
    `granted_by` VARCHAR(50) NOT NULL COMMENT '부여 관리자 표식(운영 화면에서 기입)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    PRIMARY KEY (`grant_id`),
    UNIQUE KEY `uk_newstart_grant_user_capability` (`user_id`, `capability`),
    KEY `idx_newstart_grant_capability` (`capability`),
    CONSTRAINT `fk_newstart_grant_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
