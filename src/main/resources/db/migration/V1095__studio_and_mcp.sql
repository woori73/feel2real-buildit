-- Additive only. No existing data, permissions, or original files are changed.
CREATE TABLE studio_projects (
 id VARCHAR(64) PRIMARY KEY, title VARCHAR(150) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE studio_members (
 project_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, permission VARCHAR(16) NOT NULL,
 PRIMARY KEY(project_id,user_id), FOREIGN KEY(project_id) REFERENCES studio_projects(id), FOREIGN KEY(user_id) REFERENCES users(user_id)
);
CREATE TABLE studio_files (
 id VARCHAR(36) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, sha256 CHAR(64) NOT NULL,
 storage_key VARCHAR(1024) NOT NULL, filename VARCHAR(255) NOT NULL, extension VARCHAR(8) NOT NULL,
 size_bytes BIGINT NOT NULL, uploaded_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_studio_hash(project_id,sha256), FOREIGN KEY(project_id) REFERENCES studio_projects(id)
);
CREATE TABLE studio_items (
 project_id VARCHAR(64) NOT NULL, item_key VARCHAR(100) NOT NULL, head_id VARCHAR(36), approved_id VARCHAR(36),
 PRIMARY KEY(project_id,item_key), FOREIGN KEY(project_id) REFERENCES studio_projects(id)
);
CREATE TABLE studio_revisions (
 id VARCHAR(36) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, item_key VARCHAR(100) NOT NULL,
 parent_id VARCHAR(36), label VARCHAR(40) NOT NULL, kind VARCHAR(16) NOT NULL, episode INT, cut_number INT,
 origin VARCHAR(16) NOT NULL, title VARCHAR(150) NOT NULL, content MEDIUMTEXT NOT NULL,
 dialogue TEXT NOT NULL, direction TEXT NOT NULL, work_status VARCHAR(20) NOT NULL,
 file_id VARCHAR(36), created_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_studio_revision(project_id,item_key,label), INDEX ix_studio_episode(project_id,episode,kind),
 FOREIGN KEY(project_id,item_key) REFERENCES studio_items(project_id,item_key), FOREIGN KEY(file_id) REFERENCES studio_files(id)
);
CREATE TABLE studio_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id VARCHAR(64) NOT NULL, revision_id VARCHAR(36),
 action VARCHAR(20) NOT NULL, note VARCHAR(2000) NOT NULL, actor_id BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX ix_studio_events(project_id,id),
 FOREIGN KEY(project_id) REFERENCES studio_projects(id)
);
CREATE TABLE mcp_clients (
 id VARCHAR(64) PRIMARY KEY, name VARCHAR(100) NOT NULL, redirect_uris TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE mcp_grants (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, client_id VARCHAR(64) NOT NULL,
 credential_stamp VARCHAR(64) NOT NULL, user_role VARCHAR(16) NOT NULL, scopes TEXT NOT NULL,
 expires_at TIMESTAMP NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(user_id), FOREIGN KEY(client_id) REFERENCES mcp_clients(id)
);
CREATE TABLE mcp_codes (
 hash CHAR(64) PRIMARY KEY, grant_id VARCHAR(36) NOT NULL, redirect_uri VARCHAR(1000) NOT NULL,
 challenge VARCHAR(128) NOT NULL, resource VARCHAR(255) NOT NULL, expires_at TIMESTAMP NOT NULL,
 FOREIGN KEY(grant_id) REFERENCES mcp_grants(id)
);
CREATE TABLE mcp_tokens (
 hash CHAR(64) PRIMARY KEY, grant_id VARCHAR(36) NOT NULL, kind VARCHAR(8) NOT NULL,
 expires_at TIMESTAMP NOT NULL, FOREIGN KEY(grant_id) REFERENCES mcp_grants(id), INDEX ix_mcp_grant(grant_id)
);
CREATE TABLE mcp_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, grant_id VARCHAR(36), user_id BIGINT, tool VARCHAR(80) NOT NULL,
 endpoint_id VARCHAR(150), outcome VARCHAR(20) NOT NULL, duration_ms BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX ix_mcp_audit_user(user_id,id)
);
CREATE TABLE mcp_rate_limits (
 bucket_key CHAR(64) PRIMARY KEY, window_start BIGINT NOT NULL, calls INT NOT NULL
);
CREATE TABLE mcp_pending (
 hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, request_json TEXT NOT NULL, expires_at TIMESTAMP NOT NULL
);
