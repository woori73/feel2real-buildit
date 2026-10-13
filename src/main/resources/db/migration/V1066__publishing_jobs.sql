-- DesignCraft 출판 편집기: 작업·산출물·버전 테이블
-- P0: Publishing Worker /api/publishing/* 백엔드

CREATE TABLE publishing_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  template VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  progress TINYINT NOT NULL DEFAULT 0,
  current_step VARCHAR(100),
  work_dir VARCHAR(500),
  designcraft_file VARCHAR(500),
  error TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_publishing_jobs_user (user_id),
  INDEX idx_publishing_jobs_status (status)
);

CREATE TABLE publishing_artifacts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  format VARCHAR(10) NOT NULL,
  standard VARCHAR(10),
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT,
  page_count INT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_publishing_artifacts_job (job_id),
  CONSTRAINT fk_publishing_artifacts_job FOREIGN KEY (job_id)
    REFERENCES publishing_jobs (id) ON DELETE CASCADE
);

CREATE TABLE publishing_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  designcraft_file VARCHAR(500) NOT NULL,
  label VARCHAR(200),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_publishing_versions_job (job_id),
  CONSTRAINT fk_publishing_versions_job FOREIGN KEY (job_id)
    REFERENCES publishing_jobs (id) ON DELETE CASCADE
);
