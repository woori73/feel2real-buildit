CREATE TABLE codi_call_attempts (
  id VARCHAR(36) PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  academy_id BIGINT NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(128),
  status VARCHAR(16) NOT NULL
);
