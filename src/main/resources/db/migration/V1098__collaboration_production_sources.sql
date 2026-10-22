-- Additive provenance only. Original studio files, approvals and restore history are untouched.
CREATE TABLE ai_collaboration_imports (
 id VARCHAR(36) PRIMARY KEY, workspace_id VARCHAR(64) NOT NULL,
 source_ref CHAR(40) NOT NULL, manifest_sha CHAR(64) NOT NULL, inventory_sha CHAR(64) NOT NULL,
 expected_files INT NOT NULL, registered_files INT NOT NULL DEFAULT 0,
 registered_by BIGINT NOT NULL, status VARCHAR(16) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(workspace_id) REFERENCES ai_collaboration_workspaces(id),
 UNIQUE KEY ux_collab_import(workspace_id,source_ref,inventory_sha)
);
CREATE TABLE ai_collaboration_sources (
 id VARCHAR(36) PRIMARY KEY, import_id VARCHAR(36) NOT NULL, file_id VARCHAR(36) NOT NULL,
 source_path VARCHAR(512) NOT NULL, version_label VARCHAR(32) NOT NULL,
 category VARCHAR(32) NOT NULL, source_approval VARCHAR(64) NOT NULL,
 FOREIGN KEY(import_id) REFERENCES ai_collaboration_imports(id),
 FOREIGN KEY(file_id) REFERENCES ai_collaboration_files(id),
 UNIQUE KEY ux_collab_source(import_id,source_path), UNIQUE KEY ux_collab_source_file(file_id)
);
CREATE TABLE ai_collaboration_storyboard_cuts (
 import_id VARCHAR(36) NOT NULL, cut_number INT NOT NULL, panel_file_id VARCHAR(36) NOT NULL,
 record_json LONGTEXT NOT NULL, comparison_status VARCHAR(24) NOT NULL DEFAULT 'NOT_COMPARED',
 pass_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(import_id,cut_number), FOREIGN KEY(import_id) REFERENCES ai_collaboration_imports(id),
 FOREIGN KEY(panel_file_id) REFERENCES ai_collaboration_files(id)
);
