-- Additive only. Review reports and rework tasks for the AI AGENT studio.
-- Existing studio files, revisions, approval pointers and events are not changed.
-- A review is never an approval: approval stays on studio_items.approved_id and is human-only.
CREATE TABLE studio_reviews (
 id VARCHAR(36) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, revision_id VARCHAR(36) NOT NULL,
 item_key VARCHAR(100) NOT NULL, verdict VARCHAR(16) NOT NULL, summary TEXT NOT NULL,
 issues_json MEDIUMTEXT NOT NULL, author_type VARCHAR(16) NOT NULL, ai_client VARCHAR(100),
 model_reported VARCHAR(100), mcp_grant_id VARCHAR(36), user_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80), request_sha256 CHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_studio_review_idem(project_id,idempotency_key),
 INDEX ix_studio_review_item(project_id,item_key,created_at),
 FOREIGN KEY(project_id) REFERENCES studio_projects(id), FOREIGN KEY(revision_id) REFERENCES studio_revisions(id)
);
CREATE TABLE studio_tasks (
 id VARCHAR(36) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, item_key VARCHAR(100) NOT NULL,
 revision_id VARCHAR(36), review_id VARCHAR(36), title VARCHAR(200) NOT NULL, instructions TEXT NOT NULL,
 assignee VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, result_revision_id VARCHAR(36),
 author_type VARCHAR(16) NOT NULL, ai_client VARCHAR(100), mcp_grant_id VARCHAR(36), created_by BIGINT NOT NULL,
 idempotency_key VARCHAR(80), request_sha256 CHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_studio_task_idem(project_id,idempotency_key),
 INDEX ix_studio_task_status(project_id,status,created_at),
 INDEX ix_studio_task_item(project_id,item_key),
 FOREIGN KEY(project_id) REFERENCES studio_projects(id), FOREIGN KEY(revision_id) REFERENCES studio_revisions(id),
 FOREIGN KEY(review_id) REFERENCES studio_reviews(id), FOREIGN KEY(result_revision_id) REFERENCES studio_revisions(id)
);
