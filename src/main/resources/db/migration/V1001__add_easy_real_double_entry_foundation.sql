-- ============================================================
-- V1001: Easy Phase A1 - Real double-entry foundation
--   · easy_accounts.direction ENUM에 EQUITY 추가 (V988 MODIFY 패턴 답습)
--   · 실계정 seed 13건 (멱등 INSERT, 기존 18건 불변)
--       ASSET: CASH / BANK_SHINHAN / BANK_KB / ACCT_RECV / CARD_SETTLE_RECV
--       LIABILITY: CARD_PAY_SHINHAN / CARD_PAY_KB / ACCT_PAYABLE /
--                  UNEARNED_REVENUE / LOAN_PAYABLE
--       EQUITY: OWNER_CAPITAL / RETAINED_EARNINGS
--       EXPENSE: EXPENSE_INTEREST
--   · easy_bank_account_maps: 사업체 → 실제 은행계좌 → 회계계정
--       (실계좌번호 저장 금지: safe 식별자 + masked만)
--   · easy_card_payment_maps: 카드 instrument → 미지급금 계정 → 결제 은행
--   · easy_transactions.tax_class: 세무구분 (회계분류와 분리, NULL=REVIEW_REQUIRED)
--   · SETTLEMENT 계정·기존 분개는 손대지 않는다 (historical 보존)
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. direction ENUM에 EQUITY 추가 (additive only, V988 패턴)
ALTER TABLE `easy_accounts`
    MODIFY COLUMN `direction` ENUM('INCOME','EXPENSE','ASSET','LIABILITY','EQUITY')
    NOT NULL COMMENT 'INCOME/EXPENSE/ASSET/LIABILITY/EQUITY (V1001 EQUITY 추가)';

-- 2. 실계정 seed (멱등: 코드 중복 시 스킵, 기존 sort_order와 겹치지 않게 배치)
INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'CASH', '시재(현금)', 'ASSET', 31, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'CASH');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'BANK_SHINHAN', '보통예금-신한', 'ASSET', 32, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'BANK_SHINHAN');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'BANK_KB', '보통예금-KB', 'ASSET', 33, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'BANK_KB');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'ACCT_RECV', '외상매출금(미수금)', 'ASSET', 34, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'ACCT_RECV');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'CARD_SETTLE_RECV', '카드정산미수금', 'ASSET', 35, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'CARD_SETTLE_RECV');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'CARD_PAY_SHINHAN', '미지급금-신한카드', 'LIABILITY', 91, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'CARD_PAY_SHINHAN');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'CARD_PAY_KB', '미지급금-KB카드', 'LIABILITY', 92, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'CARD_PAY_KB');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'ACCT_PAYABLE', '외상매입금', 'LIABILITY', 93, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'ACCT_PAYABLE');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'UNEARNED_REVENUE', '선수금(선수수강료)', 'LIABILITY', 94, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'UNEARNED_REVENUE');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'LOAN_PAYABLE', '차입금', 'LIABILITY', 95, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'LOAN_PAYABLE');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'OWNER_CAPITAL', '사업주자본', 'EQUITY', 100, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'OWNER_CAPITAL');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'RETAINED_EARNINGS', '이익잉여금(이월금)', 'EQUITY', 101, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'RETAINED_EARNINGS');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'EXPENSE_INTEREST', '이자비용', 'EXPENSE', 24, TRUE FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'EXPENSE_INTEREST');

-- 3. 실제 은행계좌 → 회계계정 매핑
--    한 사업체가 여러 은행계좌(동일 은행 복수계좌, 기간별 주거래 변경)를
--    각각 별도 회계계정에 연결한다. 실계좌번호 전체 저장 금지.
CREATE TABLE `easy_bank_account_maps` (
    `map_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL COMMENT '사업체',
    `bank_provider` VARCHAR(20) NOT NULL COMMENT 'SHINHAN | KB | OTHER',
    `safe_account_identifier` VARCHAR(64) NOT NULL COMMENT '안전 식별자(토큰 해시 등, 원번호 아님)',
    `masked_account` VARCHAR(20) NULL COMMENT '뒷자리 등 마스킹 표시용',
    `accounting_account_id` BIGINT NOT NULL COMMENT '연결 회계계정 (BANK_* 등 ASSET)',
    `active_from` DATE NOT NULL COMMENT '사용 시작일',
    `active_to` DATE NULL COMMENT '사용 종료일 (NULL=현행, 과거계좌도 삭제 금지)',
    `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_easy_bankmap_business` FOREIGN KEY (`business_id`)
        REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_bankmap_account` FOREIGN KEY (`accounting_account_id`)
        REFERENCES `easy_accounts` (`account_id`),
    UNIQUE KEY `uq_easy_bankmap_business_identifier` (`business_id`, `safe_account_identifier`),
    KEY `idx_easy_bankmap_business` (`business_id`),
    KEY `idx_easy_bankmap_provider` (`bank_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 카드 instrument → 미지급금 계정 → 결제 은행 매핑
--    카드 issuer와 결제은행이 같다고 가정하지 않는다 (추론 금지, 설정값이 정답).
CREATE TABLE `easy_card_payment_maps` (
    `map_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL COMMENT '사업체',
    `instrument_id` BIGINT NOT NULL COMMENT '실제 결제수단 (easy_card_instruments)',
    `card_payable_account_id` BIGINT NOT NULL COMMENT '미지급금 회계계정 (CARD_PAY_* 등 LIABILITY)',
    `payment_bank_map_id` BIGINT NOT NULL COMMENT '대금 결제 은행 (easy_bank_account_maps)',
    `active_from` DATE NOT NULL COMMENT '사용 시작일',
    `active_to` DATE NULL COMMENT '사용 종료일 (NULL=현행)',
    `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_easy_cardpaymap_business` FOREIGN KEY (`business_id`)
        REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_cardpaymap_instrument` FOREIGN KEY (`instrument_id`)
        REFERENCES `easy_card_instruments` (`instrument_id`),
    CONSTRAINT `fk_easy_cardpaymap_payable` FOREIGN KEY (`card_payable_account_id`)
        REFERENCES `easy_accounts` (`account_id`),
    CONSTRAINT `fk_easy_cardpaymap_bank` FOREIGN KEY (`payment_bank_map_id`)
        REFERENCES `easy_bank_account_maps` (`map_id`),
    UNIQUE KEY `uq_easy_cardpaymap_business_instrument` (`business_id`, `instrument_id`),
    KEY `idx_easy_cardpaymap_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 거래 세무구분 (회계 direction/account와 분리, NULL=REVIEW_REQUIRED 취급)
ALTER TABLE `easy_transactions`
    ADD COLUMN `tax_class` VARCHAR(30) NULL
    COMMENT 'INCOME_INCLUDED|EXPENSE_DEDUCTIBLE_CANDIDATE|NON_DEDUCTIBLE|NOT_INCOME|NOT_EXPENSE|REVIEW_REQUIRED (NULL=REVIEW_REQUIRED, 세법 확정 전 단정 금지)';
CREATE INDEX `idx_easy_txn_tax_class` ON `easy_transactions` (`tax_class`);
