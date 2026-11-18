CREATE TABLE parent_notice_drafts (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    owner_role VARCHAR(10) NOT NULL,
    kind VARCHAR(12) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body VARCHAR(200) NOT NULL,
    source VARCHAR(12) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_parent_notice_owner (owner_id, owner_role, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
