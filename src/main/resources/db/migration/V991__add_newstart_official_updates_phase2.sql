-- Phase 2: Official Updates Discovery System
-- Add relevance_priority, discovered_at columns
-- Add discovery_run table for scheduler tracking

ALTER TABLE newstart_official_updates
    ADD COLUMN relevance_priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' AFTER category,
    ADD COLUMN discovered_at DATETIME(6) NULL AFTER featured;

-- Discovery run tracking for scheduler safety and observability
CREATE TABLE newstart_official_discovery_run (
    run_id           BIGINT        NOT NULL AUTO_INCREMENT,
    run_started_at   DATETIME(6)   NOT NULL,
    run_finished_at  DATETIME(6)   NULL,
    source_type      VARCHAR(30)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    discovered_count INT           NOT NULL DEFAULT 0,
    new_review_count INT           NOT NULL DEFAULT 0,
    duplicate_count  INT           NOT NULL DEFAULT 0,
    irrelevant_count INT           NOT NULL DEFAULT 0,
    error_message    TEXT          NULL,
    PRIMARY KEY (run_id),
    INDEX idx_discovery_run_source_time (source_type, run_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;