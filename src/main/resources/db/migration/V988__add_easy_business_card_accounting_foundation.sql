-- EASY Phase 5A: provider-neutral business-card accounting foundation.
-- Additive only. Card observations never auto-create easy_transactions.
ALTER TABLE easy_accounts
    MODIFY COLUMN direction ENUM('INCOME', 'EXPENSE', 'ASSET', 'LIABILITY') NOT NULL;

INSERT INTO easy_accounts (code, name, direction, sort_order, active)
SELECT 'CARD_PAYABLE', '카드 미지급금', 'LIABILITY', 90, TRUE
WHERE NOT EXISTS (SELECT 1 FROM easy_accounts WHERE code = 'CARD_PAYABLE');

CREATE TABLE easy_card_instruments (
    instrument_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL,
    connection_id BIGINT NULL,
    provider_code VARCHAR(40) NOT NULL,
    issuer VARCHAR(80) NOT NULL,
    provider_instrument_token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 only; raw token/PAN forbidden',
    masked_identifier VARCHAR(40) NULL,
    last4 CHAR(4) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_easy_card_instrument_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_easy_card_instrument_connection FOREIGN KEY (connection_id) REFERENCES easy_collection_connections (connection_id),
    UNIQUE KEY uq_easy_card_instrument_token (business_id, provider_code, provider_instrument_token_hash),
    KEY idx_easy_card_instrument_connection (connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_card_billing_groups (
    billing_group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL, instrument_id BIGINT NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    period_start DATE NULL, period_end DATE NULL, payment_due_on DATE NULL,
    observed_total BIGINT NOT NULL DEFAULT 0 COMMENT 'observation only; never a new expense',
    currency CHAR(3) NOT NULL DEFAULT 'KRW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_card_billing_total CHECK (observed_total >= 0),
    CONSTRAINT fk_easy_card_billing_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_easy_card_billing_instrument FOREIGN KEY (instrument_id) REFERENCES easy_card_instruments (instrument_id),
    UNIQUE KEY uq_easy_card_billing_fingerprint (business_id, fingerprint),
    KEY idx_easy_card_billing_instrument (instrument_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_card_events (
    card_event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL, connection_id BIGINT NULL, instrument_id BIGINT NOT NULL,
    original_event_id BIGINT NULL, billing_group_id BIGINT NULL,
    event_type VARCHAR(24) NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    provider_external_id VARCHAR(160) NULL, fingerprint CHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL, amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'KRW', approval_reference_hash CHAR(64) NULL,
    reviewed_at TIMESTAMP NULL, reviewed_by VARCHAR(50) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_card_event_amount CHECK (amount > 0),
    CONSTRAINT fk_easy_card_event_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_easy_card_event_connection FOREIGN KEY (connection_id) REFERENCES easy_collection_connections (connection_id),
    CONSTRAINT fk_easy_card_event_instrument FOREIGN KEY (instrument_id) REFERENCES easy_card_instruments (instrument_id),
    CONSTRAINT fk_easy_card_event_original FOREIGN KEY (original_event_id) REFERENCES easy_card_events (card_event_id),
    CONSTRAINT fk_easy_card_event_billing FOREIGN KEY (billing_group_id) REFERENCES easy_card_billing_groups (billing_group_id),
    UNIQUE KEY uq_easy_card_event_fingerprint (business_id, fingerprint),
    KEY idx_easy_card_event_instrument_date (instrument_id, occurred_at),
    KEY idx_easy_card_event_original (original_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_card_payables (
    payable_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL, instrument_id BIGINT NOT NULL, purchase_event_id BIGINT NOT NULL,
    original_amount BIGINT NOT NULL, cancelled_amount BIGINT NOT NULL DEFAULT 0,
    settled_amount BIGINT NOT NULL DEFAULT 0, outstanding_amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'KRW', status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_card_payable_non_negative CHECK (original_amount > 0 AND cancelled_amount >= 0 AND settled_amount >= 0 AND outstanding_amount >= 0),
    CONSTRAINT chk_easy_card_payable_balance CHECK (original_amount = cancelled_amount + settled_amount + outstanding_amount),
    CONSTRAINT fk_easy_card_payable_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_easy_card_payable_instrument FOREIGN KEY (instrument_id) REFERENCES easy_card_instruments (instrument_id),
    CONSTRAINT fk_easy_card_payable_purchase FOREIGN KEY (purchase_event_id) REFERENCES easy_card_events (card_event_id),
    UNIQUE KEY uq_easy_card_payable_purchase (purchase_event_id),
    KEY idx_easy_card_payable_open (business_id, status, outstanding_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_card_settlements (
    settlement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL, instrument_id BIGINT NOT NULL, settlement_event_id BIGINT NOT NULL,
    bank_transaction_ref_hash CHAR(64) NULL, fingerprint CHAR(64) NOT NULL,
    paid_on DATE NOT NULL, amount BIGINT NOT NULL, allocated_amount BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'KRW', status VARCHAR(24) NOT NULL DEFAULT 'UNALLOCATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_card_settlement_amount CHECK (amount > 0),
    CONSTRAINT chk_easy_card_settlement_allocation CHECK (allocated_amount >= 0 AND allocated_amount <= amount),
    CONSTRAINT fk_easy_card_settlement_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_easy_card_settlement_instrument FOREIGN KEY (instrument_id) REFERENCES easy_card_instruments (instrument_id),
    CONSTRAINT fk_easy_card_settlement_event FOREIGN KEY (settlement_event_id) REFERENCES easy_card_events (card_event_id),
    UNIQUE KEY uq_easy_card_settlement_event (settlement_event_id),
    UNIQUE KEY uq_easy_card_settlement_fingerprint (business_id, fingerprint),
    KEY idx_easy_card_settlement_date (business_id, paid_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_card_settlement_allocations (
    allocation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_id BIGINT NOT NULL, payable_id BIGINT NOT NULL, allocated_amount BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_card_allocation_amount CHECK (allocated_amount > 0),
    CONSTRAINT fk_easy_card_allocation_settlement FOREIGN KEY (settlement_id) REFERENCES easy_card_settlements (settlement_id),
    CONSTRAINT fk_easy_card_allocation_payable FOREIGN KEY (payable_id) REFERENCES easy_card_payables (payable_id),
    UNIQUE KEY uq_easy_card_settlement_allocation (settlement_id, payable_id),
    KEY idx_easy_card_allocation_payable (payable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
