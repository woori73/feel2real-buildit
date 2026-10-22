CREATE TABLE unified_assistant_progress (
  user_id BIGINT NOT NULL PRIMARY KEY, role VARCHAR(10) NOT NULL,
  step INT NOT NULL DEFAULT 0, completed BIT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE unified_assistant_drafts (
  id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, role VARCHAR(10) NOT NULL,
  academy_id BIGINT NULL, business_id BIGINT NULL, operation VARCHAR(40) NOT NULL,
  payload MEDIUMTEXT NOT NULL, preview MEDIUMTEXT NOT NULL,
  plan_hash VARCHAR(64) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL, result MEDIUMTEXT NULL,
  created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
  KEY idx_unified_draft_owner (owner_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE unified_assistant_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL,
  draft_id VARCHAR(36) NULL, event VARCHAR(40) NOT NULL, created_at DATETIME(6) NOT NULL,
  KEY idx_unified_audit_owner (owner_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
