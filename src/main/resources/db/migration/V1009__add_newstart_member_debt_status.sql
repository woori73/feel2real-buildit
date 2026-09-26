-- ============================================================
-- V1009: NewStart "본인채무 확인 상태창" 저장 (Credit4U 확인 → 내 채무상황 정리)
--   · 기존 /newstart #ns-status-panel은 정적 stub(서버 저장 없음)이었다. 이 표로
--     상태창을 SOURCE OF TRUTH로 승격한다 — 별도 채무관리 화면/중복 저장 테이블을 만들지 않는다.
--   · user_id = users.user_id 그대로 PK(1:1) — V984 newstart_member_profiles와 동일한 패턴.
--   · 확인 플래그 6종은 Credit4U 공식 조회 항목과 1:1 대응한다
--     (신용정보 등록현황/신용도판단정보, 공공정보 제외 — 화면 체크리스트 기준은
--      A-to-Z §3-1 확인 항목: 개인대출/신용도판단/채무보증/채권자변동 + 등록현황 + 연체).
--   · 금액·기관명 등 채무 세부 원본은 저장하지 않는다. 사용자가 직접 정리한 메모(debt_summary)와
--     진행 단계(stage, 화면 select 옵션과 동일한 allowlist)만 저장한다.
--   · delete API는 없다(1:1 상태 row): 초기화는 값 비우고 다시 저장하는 upsert가 전부다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_member_debt_status` (
    `user_id` BIGINT NOT NULL COMMENT 'users.user_id (1:1, 새 식별자 아님)',
    `credit4u_checked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Credit4U 본인 조회를 완료했는지',
    `creditor_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '채권기관 확인',
    `personal_loan_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '개인대출정보 확인',
    `overdue_registered_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '연체 관련 등록정보(신용도판단정보) 확인',
    `debt_guarantee_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '채무보증정보 확인',
    `creditor_change_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '채권자 변동정보 확인',
    `debt_summary` VARCHAR(1000) NULL COMMENT '사용자가 직접 정리한 채무 현황 메모(기관명/금액/확인일 등)',
    `stage` VARCHAR(30) NULL COMMENT '현재 진행 단계(화면 select allowlist와 동일)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_newstart_member_debt_status_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
