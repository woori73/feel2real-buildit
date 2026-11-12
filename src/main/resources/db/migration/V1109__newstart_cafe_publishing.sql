-- 신규 빈 테이블만 추가한다. 기존 지피지기 자료·회원 로그인·MCP grant는 변경하지 않는다.
CREATE TABLE newstart_cafe_connections (
  owner_id BIGINT PRIMARY KEY,
  board_ids TEXT NOT NULL,
  access_token_enc TEXT NULL,
  refresh_token_enc TEXT NULL,
  token_expires_at TIMESTAMP(6) NULL,
  state_hash CHAR(64) NULL,
  state_expires_at TIMESTAMP(6) NULL,
  connection_epoch VARCHAR(36) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_cafe_owner FOREIGN KEY (owner_id) REFERENCES users(user_id)
);
CREATE TABLE newstart_cafe_publications (
  id VARCHAR(36) PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  grant_id VARCHAR(36) NOT NULL,
  cafe_id BIGINT NOT NULL,
  menu_id INT NOT NULL,
  board_key VARCHAR(40) NOT NULL,
  key_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  title VARCHAR(150) NOT NULL,
  content TEXT NOT NULL,
  state VARCHAR(12) NOT NULL,
  article_id BIGINT NULL,
  article_url VARCHAR(250) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uq_cafe_request (owner_id, key_hash),
  UNIQUE KEY uq_cafe_payload (cafe_id, menu_id, payload_hash),
  INDEX ix_cafe_history (owner_id, created_at),
  CONSTRAINT fk_cafe_publication_owner FOREIGN KEY (owner_id) REFERENCES users(user_id)
);
