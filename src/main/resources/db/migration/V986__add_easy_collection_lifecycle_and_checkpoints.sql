-- EASY auto-collection Phase 3B: provider events, economic-sale lifecycle, and checkpoints.
-- Additive only. Provider events are observations and never create accounting transactions.

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE easy_collection_economic_sales (
    sale_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    economic_key VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OBSERVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_collection_sale_connection FOREIGN KEY (connection_id)
        REFERENCES easy_collection_connections (connection_id),
    CONSTRAINT fk_collection_sale_business FOREIGN KEY (business_id)
        REFERENCES easy_businesses (business_id),
    UNIQUE KEY uq_collection_sale_economic (connection_id, economic_key),
    KEY idx_collection_sale_business (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_collection_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    sale_id BIGINT NOT NULL,
    raw_id BIGINT NULL,
    original_event_id BIGINT NULL,
    dataset_type VARCHAR(20) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    external_event_id VARCHAR(160) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OBSERVED',
    occurred_at TIMESTAMP NULL,
    provider_cursor VARCHAR(500) NULL,
    provider_version VARCHAR(100) NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_collection_event_connection FOREIGN KEY (connection_id)
        REFERENCES easy_collection_connections (connection_id),
    CONSTRAINT fk_collection_event_business FOREIGN KEY (business_id)
        REFERENCES easy_businesses (business_id),
    CONSTRAINT fk_collection_event_sale FOREIGN KEY (sale_id)
        REFERENCES easy_collection_economic_sales (sale_id),
    CONSTRAINT fk_collection_event_raw FOREIGN KEY (raw_id)
        REFERENCES easy_collection_raw_records (raw_id),
    CONSTRAINT fk_collection_event_original FOREIGN KEY (original_event_id)
        REFERENCES easy_collection_events (event_id),
    UNIQUE KEY uq_collection_event_key (connection_id, event_key),
    KEY idx_collection_event_sale (sale_id),
    KEY idx_collection_event_external (connection_id, external_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_collection_checkpoints (
    checkpoint_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    dataset_type VARCHAR(20) NOT NULL,
    cursor_value VARCHAR(500) NULL,
    checkpoint_value VARCHAR(500) NULL,
    last_success_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_collection_checkpoint_connection FOREIGN KEY (connection_id)
        REFERENCES easy_collection_connections (connection_id),
    UNIQUE KEY uq_collection_checkpoint (connection_id, dataset_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
