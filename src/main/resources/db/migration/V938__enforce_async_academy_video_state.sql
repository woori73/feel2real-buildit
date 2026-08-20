-- Stage 3/3: enforce the final async source/lifecycle contract, then remove V930 columns.
ALTER TABLE academy_videos
    MODIFY COLUMN lifecycle_status VARCHAR(20) NOT NULL,
    MODIFY COLUMN active_source VARCHAR(20) NOT NULL,
    MODIFY COLUMN source_version BIGINT NOT NULL,
    MODIFY COLUMN raw_cleanup_status VARCHAR(20) NOT NULL,
    DROP FOREIGN KEY fk_academy_videos_academy,
    ADD CONSTRAINT fk_academy_videos_academy_restrict
        FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_academy_videos_lifecycle
        CHECK (lifecycle_status IN ('READY', 'DELETING', 'UNAVAILABLE')),
    ADD CONSTRAINT chk_academy_videos_active_source
        CHECK (active_source IN ('RAW', 'NORMALIZED')),
    ADD CONSTRAINT chk_academy_videos_source_version
        CHECK (source_version >= 0),
    ADD CONSTRAINT chk_academy_videos_raw_cleanup
        CHECK (raw_cleanup_status IN ('NOT_REQUIRED', 'PENDING', 'DONE')),
    ADD CONSTRAINT chk_academy_videos_source_metadata
        CHECK (
            (
                active_source = 'RAW'
                AND raw_storage_key IS NOT NULL
                AND raw_media_type IS NOT NULL
                AND raw_size_bytes > 0
                AND (
                    (lifecycle_status = 'READY'
                        AND raw_cleanup_status = 'NOT_REQUIRED')
                    OR
                    (lifecycle_status = 'DELETING'
                        AND raw_cleanup_status = 'PENDING')
                )
            )
            OR
            (
                active_source = 'NORMALIZED'
                AND normalized_storage_key IS NOT NULL
                AND normalized_media_type = 'video/mp4'
                AND normalized_size_bytes > 0
                AND thumbnail_key IS NOT NULL
                AND (
                    (raw_cleanup_status = 'PENDING'
                        AND raw_storage_key IS NOT NULL
                        AND raw_media_type IS NOT NULL
                        AND raw_size_bytes > 0)
                    OR
                    (raw_cleanup_status = 'DONE'
                        AND raw_storage_key IS NULL
                        AND raw_media_type IS NULL
                        AND raw_size_bytes IS NULL)
                )
            )
        ),
    DROP INDEX idx_academy_videos_academy_status_created,
    ADD KEY idx_academy_videos_academy_lifecycle_created
        (academy_id, lifecycle_status, created_at DESC),
    ADD KEY idx_academy_videos_cleanup_reconcile
        (lifecycle_status, active_source, raw_cleanup_status, video_id),
    DROP COLUMN storage_key,
    DROP COLUMN duration_sec,
    DROP COLUMN size_bytes,
    DROP COLUMN status;
