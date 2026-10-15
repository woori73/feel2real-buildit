-- Stage 2/3: preserve every V930 row without making a legacy failure public.
UPDATE academy_videos
SET lifecycle_status = CASE status
        WHEN 'READY' THEN 'READY'
        ELSE 'UNAVAILABLE'
    END,
    active_source = 'NORMALIZED',
    normalized_storage_key = storage_key,
    normalized_media_type = 'video/mp4',
    -- V930 allowed NULL at the DB layer even though the service always wrote a positive size.
    -- Use the smallest positive sentinel so every historically legal row can reach the final invariant.
    normalized_size_bytes = GREATEST(COALESCE(size_bytes, 1), 1),
    duration_ms = CASE
        WHEN duration_sec IS NULL THEN NULL
        ELSE CAST(duration_sec AS SIGNED) * 1000
    END,
    source_version = 0,
    raw_cleanup_status = 'DONE'
WHERE lifecycle_status IS NULL;

INSERT IGNORE INTO academy_video_optimization_jobs (
    video_id,
    status,
    attempt_count,
    next_attempt_at,
    lease_owner,
    lease_token,
    lease_expires_at,
    heartbeat_at,
    progress_at,
    started_at,
    finished_at,
    last_error_code,
    diagnostic_summary,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    video_id,
    CASE status WHEN 'READY' THEN 'SUCCEEDED' ELSE 'FAILED' END,
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    created_at,
    updated_at,
    CASE status WHEN 'READY' THEN NULL ELSE 'LEGACY_FAILED' END,
    NULL,
    created_at,
    updated_at,
    created_by,
    updated_by
FROM academy_videos;
