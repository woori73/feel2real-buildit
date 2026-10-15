CREATE TABLE academy_handle_histories (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    academy_id BIGINT NOT NULL,
    handle VARCHAR(30) NOT NULL,
    valid_from_utc DATETIME(6) NOT NULL,
    valid_to_utc DATETIME(6) NULL,
    PRIMARY KEY (history_id),
    KEY idx_academy_handle_histories_lookup (handle, valid_from_utc, valid_to_utc),
    KEY idx_academy_handle_histories_academy (academy_id, valid_to_utc),
    CONSTRAINT fk_academy_handle_histories_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE
);

INSERT INTO academy_handle_histories (academy_id, handle, valid_from_utc, valid_to_utc)
SELECT academy_id, handle, COALESCE(created_at, UTC_TIMESTAMP(6)), NULL
FROM academies
WHERE handle IS NOT NULL;

CREATE TABLE academy_access_statistics (
    academy_id BIGINT NOT NULL,
    total_request_count BIGINT NOT NULL DEFAULT 0,
    last_log_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (academy_id),
    CONSTRAINT fk_academy_access_statistics_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE
);

CREATE TABLE academy_access_daily_statistics (
    academy_id BIGINT NOT NULL,
    access_date DATE NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (academy_id, access_date),
    CONSTRAINT fk_academy_access_daily_statistics_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE
);

CREATE TABLE alb_access_log_imports (
    object_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    etag VARCHAR(128) NULL,
    imported_request_count BIGINT NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (object_key_hash)
);
