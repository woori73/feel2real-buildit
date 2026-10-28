-- PROPOSAL ONLY. Not in db/migration and never auto-applied. H2/MySQL local test compatible.
CREATE TABLE exam_intake_request (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 state VARCHAR(30) NOT NULL, payload TEXT NOT NULL, offer_snapshot TEXT NOT NULL,
 consent_version VARCHAR(80) NOT NULL, consent_at TIMESTAMP NOT NULL,
 intake_key VARCHAR(80) NOT NULL, input_hash VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 UNIQUE (owner_id, intake_key)
);
CREATE INDEX ix_exam_owner_created ON exam_intake_request(owner_id, created_at);
CREATE INDEX ix_exam_state_updated ON exam_intake_request(state, updated_at);
CREATE TABLE exam_intake_document (
 id VARCHAR(36) PRIMARY KEY, request_id VARCHAR(36) NOT NULL, original_name VARCHAR(160) NOT NULL,
 content_type VARCHAR(60) NOT NULL, size_bytes BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL,
 storage_name VARCHAR(80) NOT NULL, scan_state VARCHAR(30) NOT NULL,
 hidden BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL,
 UNIQUE(request_id, sha256), FOREIGN KEY(request_id) REFERENCES exam_intake_request(id)
);
CREATE TABLE exam_intake_action (
 id VARCHAR(36) PRIMARY KEY, request_id VARCHAR(36) NOT NULL, actor_id BIGINT NOT NULL,
 kind VARCHAR(40) NOT NULL, body TEXT NOT NULL, state VARCHAR(30) NOT NULL,
 amount_won INT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 FOREIGN KEY(request_id) REFERENCES exam_intake_request(id)
);
CREATE TABLE exam_intake_delivery (
 request_id VARCHAR(36) NOT NULL, ordinal_number INT NOT NULL, actor_id BIGINT NOT NULL,
 note VARCHAR(500) NOT NULL, delivered_at TIMESTAMP NOT NULL,
 PRIMARY KEY(request_id, ordinal_number), FOREIGN KEY(request_id) REFERENCES exam_intake_request(id)
);
CREATE TABLE exam_intake_event (
 id VARCHAR(36) PRIMARY KEY, request_id VARCHAR(36) NOT NULL, event_type VARCHAR(60) NOT NULL,
 event_key VARCHAR(160) NOT NULL UNIQUE, state VARCHAR(20) NOT NULL, attempts INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL, next_attempt_at TIMESTAMP NOT NULL, acknowledged_at TIMESTAMP NULL,
 last_error VARCHAR(40) NULL, FOREIGN KEY(request_id) REFERENCES exam_intake_request(id)
);
CREATE INDEX ix_exam_event_pending ON exam_intake_event(state, next_attempt_at);
CREATE TABLE exam_intake_bank (
 id INT PRIMARY KEY, bank_name VARCHAR(80) NOT NULL, account_number VARCHAR(80) NOT NULL,
 account_holder VARCHAR(80) NOT NULL, approved BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0, changed_by BIGINT NOT NULL, approved_by BIGINT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE TABLE exam_intake_bank_audit (
 id VARCHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL,
 bank_snapshot TEXT NOT NULL, created_at TIMESTAMP NOT NULL
);
