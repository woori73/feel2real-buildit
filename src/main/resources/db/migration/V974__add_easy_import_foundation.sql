-- ============================================================
-- V974: Easy import 기반 (계정 master·공급/부가세·증빙·매핑·staging)
--   · 신규 테이블만 추가 + easy_transactions에 NULL 허용 컬럼만 추가
--     → 기존 거래 데이터와 v1 API 완전 호환 (backward compatible)
--   · 계정 seed는 잠정치 — Work 실데이터 도착 후 확정한다 (과도 seed 금지)
--   · 세율 자동계산 없음 (세법 확정 전)
--
-- 구조 참고 (코드 복사 없음):
--   · kimlawtech/korean-jangbu-for (Apache-2.0) — staging/mapping/rules 발상
--   · aivance-kr/AICassa (GitHub 표기 MIT) — 공급가액/부가세 분리·증빙유형 발상
--   · sebae1/SBS (Apache-2.0) — 적격증빙 분류 발상
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 1. 계정과목 master (내부 계정 — Work 실데이터 후 확정)
-- ============================================================
CREATE TABLE `easy_accounts` (
    `account_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(40) NOT NULL UNIQUE COMMENT '내부 계정 코드',
    `name` VARCHAR(100) NOT NULL,
    `direction` ENUM('INCOME', 'EXPENSE', 'ASSET') NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 잠정 seed (Work 실데이터 도착 후 확정·교체). UI 기본 후보와 동일 수준으로 최소 유지.
INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`) VALUES
    ('INCOME_TUITION', '수강료', 'INCOME', 1, TRUE),
    ('INCOME_BOOKS', '교재비', 'INCOME', 2, TRUE),
    ('INCOME_OTHER', '기타수입', 'INCOME', 3, TRUE),
    ('EXPENSE_SALARY', '급여/강사비', 'EXPENSE', 11, TRUE),
    ('EXPENSE_RENT', '임차료', 'EXPENSE', 12, TRUE),
    ('EXPENSE_ADMIN', '관리비', 'EXPENSE', 13, TRUE),
    ('EXPENSE_PRINT', '교재·인쇄', 'EXPENSE', 14, TRUE),
    ('EXPENSE_AD', '광고비', 'EXPENSE', 15, TRUE),
    ('EXPENSE_SUPPLIES', '소모품', 'EXPENSE', 16, TRUE),
    ('EXPENSE_FEE', '지급수수료', 'EXPENSE', 17, TRUE),
    ('EXPENSE_TAX', '세금·공과금', 'EXPENSE', 18, TRUE),
    ('EXPENSE_OTHER', '기타지출', 'EXPENSE', 19, TRUE);

-- ============================================================
-- 2. 계정 매핑 (내부 ↔ 외부/표준) — Work 데이터 도착 후 확정
-- ============================================================
CREATE TABLE `easy_account_mappings` (
    `mapping_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `account_id` BIGINT NOT NULL,
    `external_code` VARCHAR(100) NOT NULL COMMENT '이지샵 등 외부 원본 코드/명칭',
    `external_name` VARCHAR(200) NOT NULL DEFAULT '',
    `standard_code` VARCHAR(20) COMMENT '국세청 표준계정 코드 (확정 전)',
    `standard_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '국세청 표준계정명 (확정 전)',
    `note` VARCHAR(500),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`account_id`) REFERENCES `easy_accounts` (`account_id`),
    UNIQUE KEY `uk_easy_mapping_account_external` (`account_id`, `external_code`),
    INDEX `idx_easy_mapping_external` (`external_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. 자동분류 규칙 (거래처/적요 패턴 → 내부 계정)
-- ============================================================
CREATE TABLE `easy_classification_rules` (
    `rule_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `pattern_type` ENUM('COUNTERPARTY_EXACT', 'COUNTERPARTY_REGEX', 'DESCRIPTION_REGEX') NOT NULL,
    `pattern` VARCHAR(255) NOT NULL,
    `account_id` BIGINT NOT NULL,
    `priority` INT NOT NULL DEFAULT 100 COMMENT '작을수록 먼저 적용',
    `amount_min` BIGINT,
    `amount_max` BIGINT,
    `direction` ENUM('INCOME', 'EXPENSE'),
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`account_id`) REFERENCES `easy_accounts` (`account_id`),
    INDEX `idx_easy_rule_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. import staging — 원본은 staging에만, 확정분만 장부로
-- ============================================================
CREATE TABLE `easy_import_batches` (
    `batch_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `source_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '원본 파일명 등',
    `source_kind` VARCHAR(20) NOT NULL DEFAULT 'CSV' COMMENT 'BANK_CSV/CARD/MANUAL',
    `status` ENUM('STAGED', 'CONFIRMED', 'DISCARDED') NOT NULL DEFAULT 'STAGED',
    `total_rows` INT NOT NULL DEFAULT 0,
    `confirmed_rows` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    INDEX `idx_easy_batch_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_import_rows` (
    `row_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `batch_id` BIGINT NOT NULL,
    `row_no` INT NOT NULL,
    `occurred_on` DATE,
    `direction` ENUM('INCOME', 'EXPENSE'),
    `amount` BIGINT,
    `counterparty` VARCHAR(100) NOT NULL DEFAULT '',
    `description` VARCHAR(200) NOT NULL DEFAULT '',
    `pay_method` VARCHAR(30) NOT NULL DEFAULT '',
    `supply_amount` BIGINT,
    `vat_amount` BIGINT,
    `evidence_type` VARCHAR(30),
    `raw_data` TEXT COMMENT '원본 행(JSON/CSV 원문)',
    `status` ENUM('PENDING', 'READY', 'ERROR', 'IMPORTED') NOT NULL DEFAULT 'PENDING',
    `error` VARCHAR(500),
    `matched_account_id` BIGINT COMMENT '규칙 자동제안 (확정 전)',
    `confidence` DOUBLE,
    `created_txn_id` BIGINT COMMENT '확정 시 생성된 거래',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`batch_id`) REFERENCES `easy_import_batches` (`batch_id`) ON DELETE CASCADE,
    FOREIGN KEY (`matched_account_id`) REFERENCES `easy_accounts` (`account_id`) ON DELETE SET NULL,
    FOREIGN KEY (`created_txn_id`) REFERENCES `easy_transactions` (`txn_id`) ON DELETE SET NULL,
    UNIQUE KEY `uk_easy_import_batch_row` (`batch_id`, `row_no`),
    INDEX `idx_easy_import_batch_status` (`batch_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. easy_transactions 확장 — 전부 NULL 허용 (기존 행 영향 없음)
-- ============================================================
ALTER TABLE `easy_transactions`
    ADD COLUMN `account_id` BIGINT NULL COMMENT '계정 master 연결 (없으면 category 텍스트 유지)',
    ADD COLUMN `supply_amount` BIGINT NULL COMMENT '공급가액 (미분리면 NULL)',
    ADD COLUMN `vat_amount` BIGINT NULL COMMENT '부가세 (미분리면 NULL, 자동계산 없음)',
    ADD COLUMN `evidence_type` VARCHAR(30) NULL COMMENT '증빙유형 (세금계산서/신용카드/현금영수증/계산서/없음/기타)',
    ADD CONSTRAINT `chk_easy_txn_supply` CHECK (`supply_amount` IS NULL OR `supply_amount` >= 0),
    ADD CONSTRAINT `chk_easy_txn_vat` CHECK (`vat_amount` IS NULL OR `vat_amount` >= 0),
    ADD CONSTRAINT `fk_easy_txn_account`
        FOREIGN KEY (`account_id`) REFERENCES `easy_accounts` (`account_id`) ON DELETE SET NULL,
    ADD INDEX `idx_easy_txn_account` (`account_id`);
