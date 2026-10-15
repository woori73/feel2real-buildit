-- ============================================================
-- V983: EASY 자동수집 Collection Foundation (PHASE 1)
--   · 4개 테이블: sources / connections / runs / raw_records
--   · 원본 불변 보존 + dedupe_key UNIQUE = 파일 재수집 멱등성의 1차 방어
--   · 기존 easy_import_* / easy_transactions 테이블은 일체 변경하지 않는다.
--   · 고객 자격증명(ID/PW·공동인증서·OTP) 저장 컬럼 없음 —
--     connection_ref는 향후 connectedId/동의ID 등 '참조자'만 보관 (대법 2020도9972).
--   · 실제 동작 어댑터는 EXCEL_FILE(CSV 파싱)뿐. 나머지 source는 카탈로그 선등록만.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `easy_collection_sources` (
    `source_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(40) NOT NULL COMMENT 'EXCEL_FILE/CARDSALES/HOMETAX/BANK/BUSINESS_CARD/PG',
    `name` VARCHAR(100) NOT NULL,
    `adapter_type` VARCHAR(40) NOT NULL COMMENT 'MANUAL_FILE/OPENAPI/RELAY_API',
    `active` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'PHASE1는 EXCEL_FILE만 TRUE',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uq_collection_source_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_collection_connections` (
    `connection_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL,
    `label` VARCHAR(100) NOT NULL DEFAULT 'default' COMMENT '동일 source 다중 연결 구분(계좌명 등)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED (향후 NEEDS_REAUTH)',
    `connection_ref` VARCHAR(200) NULL COMMENT 'provider connectedId/동의ID 등 참조자만. 자격증명 절대 금지',
    `last_success_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_collection_conn_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_collection_conn_source` FOREIGN KEY (`source_id`) REFERENCES `easy_collection_sources` (`source_id`),
    UNIQUE KEY `uq_collection_connection` (`business_id`, `source_id`, `label`),
    KEY `idx_collection_conn_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_collection_runs` (
    `run_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `connection_id` BIGINT NOT NULL,
    `business_id` BIGINT NOT NULL,
    `trigger_type` VARCHAR(10) NOT NULL DEFAULT 'MANUAL' COMMENT 'PHASE1는 MANUAL만 (향후 AUTO)',
    `status` VARCHAR(10) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
    `source_filename` VARCHAR(200) NOT NULL DEFAULT '',
    `adapter_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '예: FILE_CSV',
    `adapter_version` VARCHAR(20) NOT NULL DEFAULT '',
    `fetched_count` INT NOT NULL DEFAULT 0,
    `accepted_count` INT NOT NULL DEFAULT 0,
    `duplicate_count` INT NOT NULL DEFAULT 0,
    `error_count` INT NOT NULL DEFAULT 0,
    `import_batch_id` BIGINT NULL COMMENT '생성된 easy_import_batches.batch_id (accepted>0일 때만)',
    `error_summary` VARCHAR(500) NULL,
    `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_collection_run_connection` FOREIGN KEY (`connection_id`) REFERENCES `easy_collection_connections` (`connection_id`),
    CONSTRAINT `fk_collection_run_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    KEY `idx_collection_run_business` (`business_id`),
    KEY `idx_collection_run_connection` (`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_collection_raw_records` (
    `raw_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `run_id` BIGINT NOT NULL,
    `connection_id` BIGINT NOT NULL,
    `business_id` BIGINT NOT NULL,
    `source_code` VARCHAR(40) NOT NULL,
    `external_record_id` VARCHAR(100) NULL COMMENT '승인번호/거래번호 등 provider 고유 ID',
    `tuple_hash` CHAR(64) NOT NULL COMMENT '정규화 내용 튜플 SHA-256 (external id 없을 때 dedupe 기반)',
    `dedupe_key` VARCHAR(160) NOT NULL COMMENT 'EXT:{id} 또는 T:{tuple-hash16}:{파일내 발생순번}',
    `raw_payload` TEXT NOT NULL COMMENT '원본 행 전문(JSON) — 불변 보존',
    `raw_hash` CHAR(64) NOT NULL COMMENT 'raw_payload SHA-256 (감사/재처리 검증용)',
    `source_occurred_on` DATE NULL,
    `status` VARCHAR(30) NOT NULL COMMENT 'STORED/IMPORT_LINKED/LEDGER_DUPLICATE_EXCLUDED/ERROR',
    `normalize_error` VARCHAR(500) NULL,
    `import_batch_id` BIGINT NULL,
    `import_row_id` BIGINT NULL,
    `ingested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_collection_raw_run` FOREIGN KEY (`run_id`) REFERENCES `easy_collection_runs` (`run_id`),
    CONSTRAINT `fk_collection_raw_connection` FOREIGN KEY (`connection_id`) REFERENCES `easy_collection_connections` (`connection_id`),
    CONSTRAINT `fk_collection_raw_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    UNIQUE KEY `uq_collection_raw_dedupe` (`dedupe_key`),
    KEY `idx_collection_raw_run` (`run_id`),
    KEY `idx_collection_raw_lookup` (`business_id`, `source_code`, `tuple_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 카탈로그 시드: PHASE 1에서 활성은 EXCEL_FILE뿐, 나머지는 향후 어댑터용 등록만.
INSERT INTO `easy_collection_sources` (`code`, `name`, `adapter_type`, `active`) VALUES
    ('EXCEL_FILE',    '엑셀/CSV 파일 수집(반자동)', 'MANUAL_FILE', TRUE),
    ('CARDSALES',     '여신금융협회 카드매출(매통조 OpenAPI)', 'OPENAPI', FALSE),
    ('HOMETAX',       '홈택스(전자세금계산서/현금영수증)', 'RELAY_API', FALSE),
    ('BANK',          '은행 거래내역', 'RELAY_API', FALSE),
    ('BUSINESS_CARD', '사업용 신용카드 지출', 'RELAY_API', FALSE),
    ('PG',            'PG 결제/정산', 'OPENAPI', FALSE);
