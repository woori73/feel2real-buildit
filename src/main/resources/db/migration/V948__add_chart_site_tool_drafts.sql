CREATE TABLE chart_regions (
  region_id VARCHAR(80) NOT NULL,
  province VARCHAR(80) NOT NULL,
  city_district VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL,
  last_researched_at DATETIME(6),
  next_research_at DATETIME(6),
  last_error TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (region_id),
  KEY idx_chart_regions_status_due (status, next_research_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO chart_regions
(region_id, province, city_district, status, created_at, updated_at)
VALUES ('seoul-gangnam', '서울특별시', '강남구', 'UNRESEARCHED', NOW(6), NOW(6));

ALTER TABLE chart_candidate_activities
  ADD COLUMN activity_date DATE NULL AFTER content_summary,
  ADD COLUMN evidence_kind VARCHAR(40) NULL AFTER activity_date,
  ADD COLUMN educationally_informative BIT(1) NULL AFTER evidence_kind,
  ADD COLUMN researched_at DATETIME(6) NULL AFTER educationally_informative;

ALTER TABLE chart_candidates
  ADD COLUMN activity_province VARCHAR(80) NULL AFTER activity_region,
  ADD COLUMN activity_city_district VARCHAR(120) NULL AFTER activity_province,
  ADD COLUMN identity_summary TEXT NULL AFTER content_summary,
  ADD COLUMN education_philosophy TEXT NULL AFTER identity_summary,
  ADD COLUMN curriculum_features TEXT NULL AFTER education_philosophy,
  ADD COLUMN teaching_features TEXT NULL AFTER curriculum_features,
  ADD COLUMN growth_advice TEXT NULL AFTER teaching_features,
  ADD COLUMN official_homepage VARCHAR(1000) NULL AFTER growth_advice,
  ADD COLUMN official_blog VARCHAR(1000) NULL AFTER official_homepage,
  ADD COLUMN official_youtube VARCHAR(1000) NULL AFTER official_blog,
  ADD COLUMN official_social VARCHAR(1000) NULL AFTER official_youtube,
  ADD COLUMN offline_activity_confirmed BIT(1) NULL AFTER official_social,
  ADD COLUMN current_year_activity_confirmed BIT(1) NULL AFTER offline_activity_confirmed,
  ADD COLUMN educator_identity_confirmed BIT(1) NULL AFTER current_year_activity_confirmed,
  ADD COLUMN exclusion_free BIT(1) NULL AFTER educator_identity_confirmed,
  ADD COLUMN eligibility_status VARCHAR(30) NULL AFTER exclusion_free,
  ADD COLUMN eligibility_reasons_json TEXT NULL AFTER eligibility_status,
  ADD COLUMN potential_signals_json TEXT NULL AFTER eligibility_reasons_json,
  ADD COLUMN research_source VARCHAR(30) NULL AFTER potential_signals_json,
  ADD COLUMN criteria_version VARCHAR(30) NULL AFTER research_source;

CREATE TABLE chart_score_snapshots (
  score_snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
  candidate_id BIGINT NOT NULL,
  review_batch_id BIGINT,
  criteria_version VARCHAR(30) NOT NULL,
  total_score INT NOT NULL,
  score_breakdown_json TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  calculated_at DATETIME(6) NOT NULL,
  proposed_rank INT NULL,
  review_status VARCHAR(30) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (score_snapshot_id),
  KEY idx_chart_score_candidate_calculated (candidate_id, calculated_at),
  KEY idx_chart_score_review_batch (review_batch_id, review_status),
  CONSTRAINT fk_chart_score_candidate FOREIGN KEY (candidate_id) REFERENCES chart_candidates(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_review_batches (
  review_batch_id BIGINT NOT NULL AUTO_INCREMENT,
  region_id VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  criteria_version VARCHAR(30) NOT NULL,
  summary TEXT NOT NULL,
  submitted_at DATETIME(6) NOT NULL,
  reviewed_at DATETIME(6),
  review_note TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (review_batch_id),
  KEY idx_chart_review_region_status (region_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_publications (
  publication_id BIGINT NOT NULL AUTO_INCREMENT,
  review_batch_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  week_start DATE NOT NULL,
  published_at DATETIME(6),
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (publication_id),
  UNIQUE KEY uk_chart_publication_week (week_start),
  KEY idx_chart_publication_status_date (status, published_at),
  CONSTRAINT fk_chart_publication_review FOREIGN KEY (review_batch_id) REFERENCES chart_review_batches(review_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_publication_entries (
  publication_entry_id BIGINT NOT NULL AUTO_INCREMENT,
  publication_id BIGINT NOT NULL,
  candidate_id BIGINT NOT NULL,
  score_snapshot_id BIGINT NOT NULL,
  entry_type VARCHAR(30) NOT NULL,
  chart_rank INT,
  previous_rank INT,
  short_description TEXT NOT NULL,
  recent_activity_summary TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (publication_entry_id),
  UNIQUE KEY uk_chart_publication_candidate (publication_id, candidate_id),
  KEY idx_chart_publication_entry_type_rank (publication_id, entry_type, chart_rank),
  CONSTRAINT fk_chart_entry_publication FOREIGN KEY (publication_id) REFERENCES chart_publications(publication_id),
  CONSTRAINT fk_chart_entry_candidate FOREIGN KEY (candidate_id) REFERENCES chart_candidates(candidate_id),
  CONSTRAINT fk_chart_entry_score FOREIGN KEY (score_snapshot_id) REFERENCES chart_score_snapshots(score_snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_web_draft_requests (
  web_draft_request_id BIGINT NOT NULL AUTO_INCREMENT,
  candidate_id BIGINT NOT NULL,
  educator_consent_at DATETIME(6) NOT NULL,
  status VARCHAR(30) NOT NULL,
  payload_json TEXT NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (web_draft_request_id),
  KEY idx_chart_web_draft_candidate_status (candidate_id, status),
  CONSTRAINT fk_chart_web_draft_candidate FOREIGN KEY (candidate_id) REFERENCES chart_candidates(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
