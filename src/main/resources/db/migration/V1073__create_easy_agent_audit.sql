CREATE TABLE IF NOT EXISTS easy_agent_sessions (
 session_id BIGINT NOT NULL AUTO_INCREMENT,business_id BIGINT NOT NULL,owner_user_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(session_id),
 KEY idx_easy_agent_session_owner(owner_user_id,business_id),
 CONSTRAINT fk_easy_agent_session_business FOREIGN KEY(business_id) REFERENCES easy_businesses(business_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS easy_agent_messages (
 message_id BIGINT NOT NULL AUTO_INCREMENT,session_id BIGINT NOT NULL,role VARCHAR(10) NOT NULL,
 kind VARCHAR(20) NOT NULL,content TEXT NOT NULL,payload_json TEXT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(message_id),
 KEY idx_easy_agent_message_session(session_id,message_id),
 CONSTRAINT fk_easy_agent_message_session FOREIGN KEY(session_id) REFERENCES easy_agent_sessions(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS easy_agent_actions (
 action_id BIGINT NOT NULL AUTO_INCREMENT,session_id BIGINT NOT NULL,business_id BIGINT NOT NULL,
 owner_user_id BIGINT NOT NULL,action_type VARCHAR(30) NOT NULL,status VARCHAR(20) NOT NULL,
 batch_id BIGINT NULL,row_id BIGINT NULL,account_id BIGINT NULL,expected_account_id BIGINT NULL,
 expected_row_status VARCHAR(20) NULL,row_ids_json TEXT NULL,summary VARCHAR(500) NOT NULL,
 expires_at DATETIME(6) NOT NULL,applied_at DATETIME(6) NULL,failure_reason VARCHAR(500) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(action_id),KEY idx_easy_agent_action_owner(owner_user_id,business_id,status),
 KEY idx_easy_agent_action_session(session_id,action_id),
 CONSTRAINT fk_easy_agent_action_session FOREIGN KEY(session_id) REFERENCES easy_agent_sessions(session_id) ON DELETE CASCADE,
 CONSTRAINT fk_easy_agent_action_business FOREIGN KEY(business_id) REFERENCES easy_businesses(business_id) ON DELETE CASCADE,
 CONSTRAINT fk_easy_agent_action_batch FOREIGN KEY(batch_id) REFERENCES easy_import_batches(batch_id) ON DELETE SET NULL,
 CONSTRAINT fk_easy_agent_action_row FOREIGN KEY(row_id) REFERENCES easy_import_rows(row_id) ON DELETE SET NULL,
 CONSTRAINT fk_easy_agent_action_account FOREIGN KEY(account_id) REFERENCES easy_accounts(account_id) ON DELETE SET NULL,
 CONSTRAINT fk_easy_agent_action_expected_account FOREIGN KEY(expected_account_id) REFERENCES easy_accounts(account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
