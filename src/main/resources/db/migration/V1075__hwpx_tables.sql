-- HWPX 편집 작업 테이블
-- DesignCraft HWPX Python Worker 통합 (publishing과 독립)

CREATE TABLE hwpx_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tutor_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  source_format VARCHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  progress TINYINT NOT NULL DEFAULT 0,
  current_step VARCHAR(100),
  work_dir VARCHAR(500),
  input_file VARCHAR(500),
  output_file VARCHAR(500),
  edits_json VARCHAR(2000),
  error TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_hwpx_jobs_tutor (tutor_id),
  INDEX idx_hwpx_jobs_status (status)
);

CREATE TABLE hwpx_artifacts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  format VARCHAR(10) NOT NULL DEFAULT 'hwpx',
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_hwpx_artifacts_job (job_id),
  CONSTRAINT fk_hwpx_artifacts_job FOREIGN KEY (job_id)
    REFERENCES hwpx_jobs (id) ON DELETE CASCADE
);
