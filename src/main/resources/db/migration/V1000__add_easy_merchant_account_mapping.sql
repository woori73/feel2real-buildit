-- ============================================================
-- V1000: Easy Phase 5A.2 - Merchant Account Mapping (additive only)
--   · Business-scoped merchant → account mapping for admin explicit mappings
--   · Normalized merchant key for historical/rule-based matching
--   · Source tracking for recommendation transparency
--   · No auto-posting; recommendation only
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `easy_merchant_account_mappings` (
    `mapping_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL COMMENT 'Business scope - same merchant may map to different accounts per business',
    `normalized_merchant` VARCHAR(160) NOT NULL COMMENT 'Normalized merchant name (trim, case, whitespace)',
    `raw_merchant` VARCHAR(160) NOT NULL COMMENT 'Original merchant name as seen in card event (issuer + providerExternalId)',
    `account_id` BIGINT NOT NULL COMMENT 'Recommended EasyAccount',
    `source` VARCHAR(30) NOT NULL COMMENT 'ADMIN_MAPPING | HISTORICAL_EXACT | HISTORICAL_NORMALIZED | RULE',
    `confidence` VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH | MEDIUM | LOW',
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_by` VARCHAR(50) NULL COMMENT 'Admin who explicitly saved this mapping',
    `updated_by` VARCHAR(50) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_easy_merchant_mapping_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_merchant_mapping_account` FOREIGN KEY (`account_id`) REFERENCES `easy_accounts` (`account_id`),
    UNIQUE KEY `uq_easy_merchant_mapping_business_merchant` (`business_id`, `normalized_merchant`),
    KEY `idx_easy_merchant_mapping_account` (`account_id`),
    KEY `idx_easy_merchant_mapping_source` (`source`),
    KEY `idx_easy_merchant_mapping_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for historical lookups by raw merchant (for normalization matching)
CREATE INDEX `idx_easy_merchant_mapping_raw_merchant` ON `easy_merchant_account_mappings` (`business_id`, `raw_merchant`);
