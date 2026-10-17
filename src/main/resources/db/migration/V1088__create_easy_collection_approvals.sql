-- P1: 수집 자료 장부 반영 승인 테이블
-- 운영 DB 적용 금지 (PR 병합 후 별도 승인 필요)

CREATE TABLE IF NOT EXISTS easy_collection_approvals (
  approval_id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  requested_at DATETIME NOT NULL,
  requested_by VARCHAR(100),
  decided_at DATETIME,
  decided_by VARCHAR(100),
  decision_note VARCHAR(500),
  idempotency_key VARCHAR(100),
  created_at DATETIME,
  updated_at DATETIME,
  PRIMARY KEY (approval_id),
  UNIQUE KEY uk_approval_run (run_id),
  UNIQUE KEY uk_approval_idempotency (idempotency_key),
  KEY idx_approval_business_status (business_id, status),
  CONSTRAINT fk_approval_run FOREIGN KEY (run_id) REFERENCES easy_collection_runs (run_id),
  CONSTRAINT fk_approval_business FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
