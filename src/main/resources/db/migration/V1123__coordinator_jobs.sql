-- F2R AI Coordinator 1단계(2026-11-19): AI가 MCP로 만든 업무 초안과 사람 승인 기록. 추가만 하고 기존 테이블은 바꾸지 않는다.
-- 상태: review_required → approved → completed / rejected / expired / failed. 시각은 한국 시간(KST) 벽시계.
CREATE TABLE coord_jobs (
  job_id CHAR(36) NOT NULL,
  user_id BIGINT NOT NULL COMMENT '요청·승인 계정(본인 작업만 볼 수 있다)',
  business_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL COMMENT '중복 방지 키(같은 계정·같은 키 = 같은 작업)',
  kind VARCHAR(30) NOT NULL COMMENT 'ATTENDANCE',
  status VARCHAR(20) NOT NULL,
  source VARCHAR(20) NOT NULL COMMENT 'MCP/MOBILE/WEB',
  client_name VARCHAR(100) NULL COMMENT 'MCP 연결 이름(신고값)',
  plan_json TEXT NOT NULL COMMENT '승인 화면에 보여 주고 승인 후 그대로 실행하는 계획',
  plan_hash CHAR(64) NOT NULL,
  result_json TEXT NULL,
  error VARCHAR(500) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (job_id),
  UNIQUE KEY uq_coord_jobs_request (user_id, request_id),
  KEY idx_coord_jobs_user_status (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 상태 변경 감사 기록(누가·언제·무엇을). 수정·삭제 API 없음.
CREATE TABLE coord_job_events (
  event_id BIGINT NOT NULL AUTO_INCREMENT,
  job_id CHAR(36) NOT NULL,
  at DATETIME(6) NOT NULL,
  actor VARCHAR(20) NOT NULL COMMENT 'MCP/USER/SERVER',
  actor_login_id VARCHAR(50) NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  message VARCHAR(500) NULL,
  PRIMARY KEY (event_id),
  KEY idx_coord_job_events_job (job_id, at),
  CONSTRAINT fk_coord_job_events_job FOREIGN KEY (job_id) REFERENCES coord_jobs (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
