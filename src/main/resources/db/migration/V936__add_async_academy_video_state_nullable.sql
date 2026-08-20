-- Stage 1/3: add the async academy-video model without invalidating legacy V930 rows.
-- Existing academy_videos data is backfilled in V937 before V938 enforces the final contract.
ALTER TABLE academy_videos
    MODIFY COLUMN thumbnail_key VARCHAR(500) NULL,
    ADD COLUMN lifecycle_status VARCHAR(20) NULL AFTER status,
    ADD COLUMN active_source VARCHAR(20) NULL AFTER lifecycle_status,
    ADD COLUMN raw_storage_key VARCHAR(500) NULL AFTER active_source,
    ADD COLUMN raw_media_type VARCHAR(100) NULL AFTER raw_storage_key,
    ADD COLUMN raw_size_bytes BIGINT NULL AFTER raw_media_type,
    ADD COLUMN normalized_storage_key VARCHAR(500) NULL AFTER raw_size_bytes,
    ADD COLUMN normalized_media_type VARCHAR(100) NULL AFTER normalized_storage_key,
    ADD COLUMN normalized_size_bytes BIGINT NULL AFTER normalized_media_type,
    ADD COLUMN duration_ms BIGINT NULL AFTER thumbnail_key,
    ADD COLUMN source_version BIGINT NULL AFTER height,
    ADD COLUMN raw_cleanup_status VARCHAR(20) NULL AFTER source_version;

CREATE TABLE academy_video_upload_sessions (
    session_id CHAR(36) PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    requested_filename VARCHAR(255) NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    staging_key VARCHAR(500) NOT NULL,
    multipart_upload_id VARCHAR(500) NOT NULL,
    part_size_bytes BIGINT NOT NULL,
    expected_part_count INT NOT NULL,
    deadline_at DATETIME(6) NOT NULL,
    finalization_token CHAR(36) NULL,
    part_manifest_json JSON NULL,
    manifest_hash CHAR(64) NULL,
    next_finalize_attempt_at DATETIME(6) NULL,
    finalize_recovery_owner VARCHAR(100) NULL,
    finalize_recovery_lease_expires_at DATETIME(6) NULL,
    finalization_started_at DATETIME(6) NULL,
    finalization_completed_at DATETIME(6) NULL,
    completed_object_etag VARCHAR(255) NULL,
    completed_object_size_bytes BIGINT NULL,
    accepted_video_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    aborted_at DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uk_academy_video_upload_sessions_accepted_video UNIQUE (accepted_video_id),
    CONSTRAINT fk_academy_video_upload_sessions_academy
        FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_academy_video_upload_sessions_accepted_video
        FOREIGN KEY (accepted_video_id) REFERENCES academy_videos(video_id) ON DELETE RESTRICT,
    CONSTRAINT chk_academy_video_upload_session_size
        CHECK (declared_size_bytes BETWEEN 1 AND 209715200),
    CONSTRAINT chk_academy_video_upload_session_parts
        CHECK (part_size_bytes > 0 AND expected_part_count BETWEEN 1 AND 10),
    CONSTRAINT chk_academy_video_upload_session_state
        CHECK (state IN ('INITIATED', 'COMPLETING', 'ACCEPTED', 'REJECTED', 'ABORTED', 'EXPIRED')),
    CONSTRAINT chk_academy_video_upload_session_finalization
        CHECK (
            (state = 'COMPLETING'
                AND finalization_token IS NOT NULL
                AND part_manifest_json IS NOT NULL
                AND manifest_hash IS NOT NULL)
            OR
            (state <> 'COMPLETING' AND finalization_token IS NULL)
        ),
    CONSTRAINT chk_academy_video_upload_session_recovery_lease
        CHECK (
            (finalize_recovery_owner IS NULL AND finalize_recovery_lease_expires_at IS NULL)
            OR
            (state = 'COMPLETING'
                AND finalize_recovery_owner IS NOT NULL
                AND finalize_recovery_lease_expires_at IS NOT NULL)
        ),
    CONSTRAINT chk_academy_video_upload_session_acceptance
        CHECK (
            (state = 'ACCEPTED' AND accepted_video_id IS NOT NULL)
            OR
            (state <> 'ACCEPTED' AND accepted_video_id IS NULL)
        ),
    KEY idx_academy_video_upload_sessions_state_deadline (state, deadline_at),
    KEY idx_academy_video_upload_sessions_academy_state_deadline
        (academy_id, state, deadline_at),
    KEY idx_academy_video_upload_sessions_finalize_due
        (state, next_finalize_attempt_at, finalize_recovery_lease_expires_at),
    KEY idx_academy_video_upload_sessions_terminal_reconcile
        (state, accepted_video_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academy_video_optimization_jobs (
    job_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    lease_owner VARCHAR(100) NULL,
    lease_token CHAR(36) NULL,
    lease_expires_at DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NULL,
    progress_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    diagnostic_summary VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uk_academy_video_optimization_jobs_video UNIQUE (video_id),
    CONSTRAINT fk_academy_video_optimization_jobs_video
        FOREIGN KEY (video_id) REFERENCES academy_videos(video_id) ON DELETE RESTRICT,
    CONSTRAINT chk_academy_video_optimization_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_academy_video_optimization_job_attempt
        CHECK (attempt_count BETWEEN 0 AND 2),
    CONSTRAINT chk_academy_video_optimization_job_lease
        CHECK (
            (status = 'PROCESSING'
                AND attempt_count BETWEEN 1 AND 2
                AND lease_owner IS NOT NULL
                AND lease_token IS NOT NULL
                AND lease_expires_at IS NOT NULL
                AND heartbeat_at IS NOT NULL)
            OR
            (status <> 'PROCESSING'
                AND lease_owner IS NULL
                AND lease_token IS NULL
                AND lease_expires_at IS NULL
                AND heartbeat_at IS NULL)
        ),
    KEY idx_academy_video_optimization_jobs_claim
        (status, next_attempt_at, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academy_video_asset_cleanups (
    cleanup_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dedupe_key CHAR(64) NOT NULL,
    session_id CHAR(36) NULL,
    video_id BIGINT NULL,
    job_id BIGINT NULL,
    asset_class VARCHAR(40) NOT NULL,
    asset_key VARCHAR(500) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    lease_owner VARCHAR(100) NULL,
    lease_token CHAR(36) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uk_academy_video_asset_cleanups_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_academy_video_asset_cleanups_session
        FOREIGN KEY (session_id) REFERENCES academy_video_upload_sessions(session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_academy_video_asset_cleanups_video
        FOREIGN KEY (video_id) REFERENCES academy_videos(video_id) ON DELETE RESTRICT,
    CONSTRAINT fk_academy_video_asset_cleanups_job
        FOREIGN KEY (job_id) REFERENCES academy_video_optimization_jobs(job_id) ON DELETE RESTRICT,
    CONSTRAINT chk_academy_video_asset_cleanup_state
        CHECK (state IN ('PENDING', 'PROCESSING', 'DONE')),
    CONSTRAINT chk_academy_video_asset_cleanup_class
        CHECK (asset_class IN (
            'MULTIPART_UPLOAD',
            'STAGED_OBJECT',
            'VALIDATION_TEMP',
            'RAW_VIDEO',
            'NORMALIZED_VIDEO',
            'THUMBNAIL',
            'ATTEMPT_OUTPUT'
        )),
    CONSTRAINT chk_academy_video_asset_cleanup_attempt
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_academy_video_asset_cleanup_lease
        CHECK (
            (state = 'PROCESSING'
                AND lease_owner IS NOT NULL
                AND lease_token IS NOT NULL
                AND lease_expires_at IS NOT NULL)
            OR
            (state <> 'PROCESSING'
                AND lease_owner IS NULL
                AND lease_token IS NULL
                AND lease_expires_at IS NULL)
        ),
    KEY idx_academy_video_asset_cleanups_due (state, next_attempt_at, lease_expires_at),
    KEY idx_academy_video_asset_cleanups_session (session_id),
    KEY idx_academy_video_asset_cleanups_video (video_id),
    KEY idx_academy_video_asset_cleanups_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
