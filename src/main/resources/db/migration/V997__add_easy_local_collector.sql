-- EASY Visible Local Browser Collector V1: foundation only.
-- A registered local collector (an agent process running on the business's own PC) pulls tasks
-- and drives a VISIBLE browser the human authenticates in; EASY never receives or stores any
-- site-issued human-auth secret or card identifier. Additive only. No accounting tables touched.
CREATE TABLE easy_local_collectors (
    collector_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 of the pairing token; raw token is shown once at registration and never stored',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
    last_heartbeat_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_easy_local_collector_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    UNIQUE KEY uq_easy_local_collector_token (token_hash),
    KEY idx_easy_local_collector_business (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE easy_collector_tasks (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collector_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    source_code VARCHAR(40) NOT NULL COMMENT 'e.g. CARD_COMPANY_TEST -- free-text workflow key, not tied to easy_collection_sources in V1',
    data_type VARCHAR(100) NOT NULL COMMENT 'e.g. 카드 이용내역 -- free text, display only',
    requested_from DATE NOT NULL,
    requested_to DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'STARTING'
        COMMENT 'STARTING/BROWSER_OPEN/WAITING_FOR_USER_AUTH/AUTHENTICATED/COLLECTING/DOWNLOADING/COMPLETED/FAILED/CANCELLED',
    error_summary VARCHAR(500) NULL,
    downloaded_filename VARCHAR(200) NULL,
    downloaded_size BIGINT NULL,
    downloaded_sha256 CHAR(64) NULL,
    downloaded_at TIMESTAMP NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL, updated_by VARCHAR(50) NULL,
    CONSTRAINT chk_easy_collector_task_dates CHECK (requested_from <= requested_to),
    CONSTRAINT fk_easy_collector_task_collector FOREIGN KEY (collector_id) REFERENCES easy_local_collectors (collector_id),
    CONSTRAINT fk_easy_collector_task_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id),
    KEY idx_easy_collector_task_collector_status (collector_id, status),
    KEY idx_easy_collector_task_business (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
