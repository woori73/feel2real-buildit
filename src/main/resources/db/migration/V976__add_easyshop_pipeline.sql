-- ============================================================
-- V976: EasyShop 실데이터 파이프라인 (복식분개·신고대조·수정로그)
--   · V973~V975 절대 불변 — 신규 테이블 + easy_import_rows NULL 확장만
--   · 복식분개는 확정 장부의 이중 표현이다 (같은 경제거래 이중 계상 없음:
--     거래당 활성 분개 1건, 수정 시 기존 분개 void + 재분개)
--   · 차변 합계 == 대변 합계는 서비스에서 강제 + DB CHECK로 보증한다
--   · 신고자료 대조는 역산하지 않는다 — 장부 정상 계산 vs 기준자료 독립 비교,
--     설명 불가 차이는 UNKNOWN으로 둔다
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 1. easy_import_rows 확장 — 전부 NULL/기존값 호환
--   · external_account: 이지샵 원본 계정명 (매핑 리뷰용, 추측 매핑 없음)
--   · status DUPLICATE 추가: 장부에 이미 있는 fingerprint는 확정 제외
-- ============================================================
ALTER TABLE `easy_import_rows`
    ADD COLUMN `external_account` VARCHAR(100) NULL COMMENT '원본 계정명 (이지샵 등, 매핑 전 원문 보존)',
    MODIFY COLUMN `status` ENUM('PENDING', 'READY', 'ERROR', 'IMPORTED', 'DUPLICATE')
        NOT NULL DEFAULT 'PENDING' COMMENT 'DUPLICATE=장부 중복 의심으로 확정 제외';

-- ============================================================
-- 2. 복식분개 — 확정 거래의 차변/대변 이중 표현
-- ============================================================
CREATE TABLE `easy_journal_entries` (
    `entry_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `occurred_on` DATE NOT NULL,
    `txn_id` BIGINT NULL COMMENT '원천 거래 (삭제 시 NULL, 분개 이력은 보존)',
    `status` ENUM('POSTED', 'VOIDED') NOT NULL DEFAULT 'POSTED',
    `memo` VARCHAR(500) NULL,
    `reversed_entry_id` BIGINT NULL COMMENT '재분개 시 void된 이전 분개',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    FOREIGN KEY (`txn_id`) REFERENCES `easy_transactions` (`txn_id`) ON DELETE SET NULL,
    FOREIGN KEY (`reversed_entry_id`) REFERENCES `easy_journal_entries` (`entry_id`)
        ON DELETE SET NULL,
    INDEX `idx_easy_journal_business_date` (`business_id`, `occurred_on`),
    INDEX `idx_easy_journal_txn` (`txn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_journal_lines` (
    `line_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `entry_id` BIGINT NOT NULL,
    `account_id` BIGINT NOT NULL COMMENT 'P&L 계정 또는 자금정산 contra 계정',
    `debit` BIGINT NOT NULL DEFAULT 0 COMMENT '차변 (원)',
    `credit` BIGINT NOT NULL DEFAULT 0 COMMENT '대변 (원)',
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `chk_easy_line_non_negative`
        CHECK (`debit` >= 0 AND `credit` >= 0),
    CONSTRAINT `chk_easy_line_single_side`
        CHECK ((`debit` > 0 AND `credit` = 0) OR (`credit` > 0 AND `debit` = 0)),
    FOREIGN KEY (`entry_id`) REFERENCES `easy_journal_entries` (`entry_id`)
        ON DELETE CASCADE,
    FOREIGN KEY (`account_id`) REFERENCES `easy_accounts` (`account_id`),
    INDEX `idx_easy_line_entry` (`entry_id`),
    INDEX `idx_easy_line_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 분개 contra(자금정산) 내부 계정 — 세무 계정이 아니라 복식 균형을 위한 정산 장치다.
INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    VALUES ('SETTLEMENT', '자금정산', 'ASSET', 0, TRUE);

-- ============================================================
-- 3. 과거 신고 기준자료 — Library 확보 자료를 손으로 등록한다 (seed 없음)
--   · 장부에 맞추는 역산 금지 — 독립 비교용 reference다
-- ============================================================
CREATE TABLE `easy_filing_references` (
    `ref_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `fiscal_year` INT NOT NULL COMMENT '귀속연도',
    `revenue` BIGINT NULL COMMENT '신고서 수입금액 (원)',
    `expense` BIGINT NULL COMMENT '신고서 필요경비 (원, 자료 없으면 NULL)',
    `income_amount` BIGINT NULL COMMENT '신고서 소득금액 (원, 자료 없으면 NULL)',
    `source` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '기준자료 출처 (예: Library 종합소득세 신고서)',
    `note` VARCHAR(500) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `chk_easy_ref_revenue` CHECK (`revenue` IS NULL OR `revenue` >= 0),
    CONSTRAINT `chk_easy_ref_expense` CHECK (`expense` IS NULL OR `expense` >= 0),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    UNIQUE KEY `uk_easy_ref_business_year` (`business_id`, `fiscal_year`),
    INDEX `idx_easy_ref_year` (`fiscal_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. 수정 감사 로그 — append-only (요청→확인→수정→재분개 추적용)
-- ============================================================
CREATE TABLE `easy_correction_logs` (
    `log_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `txn_id` BIGINT NULL COMMENT '대상 거래 (삭제 시 NULL, 스냅샷은 보존)',
    `action` VARCHAR(30) NOT NULL COMMENT 'UPDATE/RESOLVE/VOID_REPOST 등',
    `before_json` TEXT NOT NULL COMMENT '수정 전 스냅샷(JSON)',
    `after_json` TEXT NOT NULL COMMENT '수정 후 스냅샷(JSON)',
    `requested_by` VARCHAR(50) NULL,
    `confirmed_by` VARCHAR(50) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    FOREIGN KEY (`txn_id`) REFERENCES `easy_transactions` (`txn_id`) ON DELETE SET NULL,
    INDEX `idx_easy_clog_business` (`business_id`),
    INDEX `idx_easy_clog_txn` (`txn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
