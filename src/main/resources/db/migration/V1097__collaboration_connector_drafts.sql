-- Separate append-only collaboration permission. Existing read-only grants remain unchanged.
CREATE TABLE ai_collaboration_workspaces (
 id VARCHAR(64) PRIMARY KEY, title VARCHAR(150) NOT NULL, owner_id BIGINT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(owner_id) REFERENCES users(user_id)
);
INSERT INTO ai_collaboration_workspaces(id,title) VALUES('ai-agent-part1','《AI AGENT》 1부');
CREATE TABLE ai_collaboration_workspace_members (
 workspace_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, permission VARCHAR(16) NOT NULL,
 PRIMARY KEY(workspace_id,user_id), FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 FOREIGN KEY(user_id) REFERENCES users(user_id)
);
ALTER TABLE ai_collaboration_tasks ADD COLUMN workspace_id VARCHAR(64) NULL,
 ADD CONSTRAINT fk_collab_task_workspace FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 ADD INDEX ix_collab_workspace(workspace_id,created_at);
CREATE TABLE ai_collaboration_workspace_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, workspace_id VARCHAR(64) NOT NULL,
 actor_id BIGINT NOT NULL, actor_agent VARCHAR(20), event_type VARCHAR(32) NOT NULL,
 body TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 INDEX ix_collab_memory(workspace_id,id)
);
CREATE TABLE mcp_collaboration_bindings (
 grant_id VARCHAR(36) PRIMARY KEY, workspace_id VARCHAR(64) NOT NULL, agent VARCHAR(16) NOT NULL,
 bound_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(grant_id) REFERENCES mcp_grants(id), FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id)
);
CREATE TABLE ai_collaboration_files (
 id VARCHAR(36) PRIMARY KEY, workspace_id VARCHAR(64) NOT NULL, parent_id VARCHAR(36),
 storage_key VARCHAR(1024) NOT NULL, filename VARCHAR(255) NOT NULL, extension VARCHAR(8) NOT NULL,
 sha256 CHAR(64) NOT NULL, size_bytes BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 registered_by BIGINT NOT NULL, registered_agent VARCHAR(16), source_grant_id VARCHAR(36),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 FOREIGN KEY(parent_id) REFERENCES ai_collaboration_files(id), INDEX ix_collab_file_sha(workspace_id,sha256)
);
CREATE TABLE ai_collaboration_uploads (
 id VARCHAR(36) PRIMARY KEY, grant_id VARCHAR(36) NOT NULL, workspace_id VARCHAR(64) NOT NULL,
 task_id VARCHAR(36) NOT NULL, agent VARCHAR(16) NOT NULL, filename VARCHAR(255) NOT NULL,
 expected_sha CHAR(64) NOT NULL, expected_size BIGINT NOT NULL, received_size BIGINT NOT NULL DEFAULT 0,
 next_chunk INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING',
 parent_id VARCHAR(36), file_id VARCHAR(36), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(grant_id) REFERENCES mcp_grants(id), FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 FOREIGN KEY(task_id) REFERENCES ai_collaboration_tasks(id)
);
CREATE TABLE ai_collaboration_chunks (
 upload_id VARCHAR(36) NOT NULL, chunk_index INT NOT NULL, sha256 CHAR(64) NOT NULL,
 data MEDIUMBLOB NOT NULL, PRIMARY KEY(upload_id,chunk_index),
 FOREIGN KEY(upload_id) REFERENCES ai_collaboration_uploads(id)
);
CREATE TABLE ai_collaboration_submissions (
 grant_id VARCHAR(36) NOT NULL, request_id VARCHAR(64) NOT NULL, task_id VARCHAR(36) NOT NULL,
 body_sha CHAR(64) NOT NULL, result_json TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(grant_id,request_id), FOREIGN KEY(task_id) REFERENCES ai_collaboration_tasks(id)
);
