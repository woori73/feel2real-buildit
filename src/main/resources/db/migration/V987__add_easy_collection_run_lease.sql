-- EASY Phase 4: durable run ownership and lease metadata.
-- Nullable columns preserve compatibility with runs created before lease support.
ALTER TABLE easy_collection_runs
    ADD COLUMN lease_token VARCHAR(64) NULL COMMENT 'random execution ownership identifier, never a provider credential',
    ADD COLUMN lease_acquired_at TIMESTAMP NULL,
    ADD COLUMN lease_expires_at TIMESTAMP NULL,
    ADD COLUMN heartbeat_at TIMESTAMP NULL,
    ADD INDEX idx_collection_run_active_lease (connection_id, status, lease_expires_at);
