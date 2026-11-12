-- Additive only. Two-step cleanup of retired Studio versions (director decision 2026-11-12).
-- Step 1 archive: archived_at hides a non-head, non-approved version from lists and search; restorable.
-- Step 2 purge: only versions archived 14+ days ago, only the IDs the director confirmed.
-- Existing rows keep archived_at NULL, so nothing is hidden or deleted by this migration.
ALTER TABLE studio_revisions
  ADD COLUMN archived_at TIMESTAMP NULL,
  ADD COLUMN archived_by BIGINT NULL,
  ADD COLUMN archive_reason VARCHAR(300) NULL,
  ADD COLUMN archive_scope VARCHAR(8) NULL;
-- archive_scope: VERSION(앞선 버전 하나) / ITEM(감독이 지정한 작업 ID 전체, 최신본 포함).
CREATE INDEX ix_studio_revision_archived ON studio_revisions(project_id, archived_at);

-- Permanent record of every archive / restore / purge. Kept even after the version row is purged.
CREATE TABLE studio_cleanup_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id VARCHAR(64) NOT NULL, action VARCHAR(16) NOT NULL,
 revision_id VARCHAR(36) NOT NULL, item_key VARCHAR(100) NOT NULL, label VARCHAR(40) NOT NULL,
 file_id VARCHAR(36), bytes_freed BIGINT NOT NULL DEFAULT 0, reason VARCHAR(300) NOT NULL,
 note VARCHAR(500) NOT NULL, actor_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_studio_cleanup_log(project_id, id), FOREIGN KEY(project_id) REFERENCES studio_projects(id)
);
