-- ============================================================
-- V1003: Easy Phase A2 - Cut-over foundation
--   · easy_cutovers: 사업체별 복식 전환 기준일 + 개시분개 링크
--       (DRAFT/APPROVED, APPROVED 전에는 B/S opening source로 사용 금지)
--   · 기존 easy_annual_closings(마감 상태기) 재사용 — 중복 테이블 없음
--   · 기존 journal/계정 불변, historical 수정 없음
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `easy_cutovers` (
    `cutover_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL COMMENT '사업체',
    `cutover_date` DATE NOT NULL COMMENT '이 날짜 이후 승인분부터 실계정 복식',
    `opening_entry_id` BIGINT NULL COMMENT '개시잔액 복식분개 (easy_journal_entries)',
    `status` VARCHAR(10) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | APPROVED',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_easy_cutover_business` FOREIGN KEY (`business_id`)
        REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_cutover_entry` FOREIGN KEY (`opening_entry_id`)
        REFERENCES `easy_journal_entries` (`entry_id`),
    UNIQUE KEY `uq_easy_cutover_business` (`business_id`),
    KEY `idx_easy_cutover_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
