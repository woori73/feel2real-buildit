-- Additive, follows the held #854 V1094 and #855 V1095. No production writes by this task.
CREATE TABLE ai_collaboration_budget_days (
 budget_day DATE PRIMARY KEY,
 reserved_microusd BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ai_collaboration_tasks (
 id VARCHAR(36) PRIMARY KEY,
 project_id VARCHAR(64) NOT NULL,
 owner_id BIGINT NOT NULL,
 title VARCHAR(150) NOT NULL,
 instruction TEXT NOT NULL,
 participants VARCHAR(100) NOT NULL,
 status VARCHAR(24) NOT NULL,
 phase_index INT NOT NULL DEFAULT 0,
 max_calls INT NOT NULL,
 calls_used INT NOT NULL DEFAULT 0,
 budget_microusd BIGINT NOT NULL,
 reserved_microusd BIGINT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 active_run VARCHAR(36),
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(project_id) REFERENCES studio_projects(id),
 FOREIGN KEY(owner_id) REFERENCES users(user_id),
 INDEX ix_collaboration_project(project_id,created_at)
);
CREATE TABLE ai_collaboration_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL,
 actor VARCHAR(20) NOT NULL,
 actor_user_id BIGINT,
 event_type VARCHAR(32) NOT NULL,
 body MEDIUMTEXT NOT NULL,
 run_id VARCHAR(36),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(task_id) REFERENCES ai_collaboration_tasks(id),
 INDEX ix_collaboration_timeline(task_id,id)
);
CREATE TABLE ai_collaboration_artifacts (
 id VARCHAR(36) PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL,
 file_id VARCHAR(36),
 revision_id VARCHAR(36),
 pr_number INT,
 created_by BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(task_id) REFERENCES ai_collaboration_tasks(id),
 FOREIGN KEY(file_id) REFERENCES studio_files(id),
 FOREIGN KEY(revision_id) REFERENCES studio_revisions(id)
);
