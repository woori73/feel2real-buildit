CREATE TABLE landing_video_slots (
    slot VARCHAR(40) PRIMARY KEY,
    original_filename VARCHAR(255) NULL,
    storage_key VARCHAR(500) NULL,
    thumbnail_key VARCHAR(500) NULL,
    duration_sec INT NULL,
    size_bytes BIGINT NULL,
    width INT NULL,
    height INT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO landing_video_slots (
    slot,
    is_visible,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES
    ('HERO_BACKGROUND', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, NULL),
    ('ACADEMY_SHORTS_DEMO', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, NULL),
    ('PARENT_REPORT_DEMO', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, NULL);
