CREATE TABLE chart_discovery_settings (
  id VARCHAR(40) NOT NULL,
  search_zone VARCHAR(60) NOT NULL,
  include_activities TEXT NOT NULL,
  excluded_targets TEXT NOT NULL,
  candidate_limit INT NOT NULL,
  deep_search_enabled BIT(1) NOT NULL,
  extra_instruction TEXT NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO chart_discovery_settings
(id, search_zone, include_activities, excluded_targets, candidate_limit, deep_search_enabled, extra_instruction, created_at, updated_at)
VALUES
('default', 'apgujeong-sinsa', '수업, 내신, 시험분석, 교육칼럼, YouTube, Shorts',
 '대형학원, 전국 프랜차이즈, 체인 학원, 인강 중심 강사, 스타강사, 채용공고, 단순 광고/순위글',
 10, b'1', '지역에서 실제 수업활동을 공개하는 원장/강사를 우선 발굴하세요. 검색 품질 확인 전 강남구 전체로 확대하지 마세요.', NOW(6), NOW(6));

CREATE TABLE chart_candidates (
  candidate_id BIGINT NOT NULL AUTO_INCREMENT,
  zone_id VARCHAR(60) NOT NULL,
  person_key VARCHAR(180) NOT NULL,
  name VARCHAR(80) NOT NULL,
  role VARCHAR(40) NOT NULL,
  academy_name VARCHAR(160),
  activity_region VARCHAR(120) NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  content_summary TEXT NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  source_url_hash VARCHAR(64) NOT NULL,
  source_title VARCHAR(500),
  discovery_reason TEXT NOT NULL,
  deep_search_result TEXT NOT NULL,
  exclusion_check TEXT NOT NULL,
  verification_status VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  google_query VARCHAR(500) NOT NULL,
  discovered_at DATETIME(6) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (candidate_id),
  UNIQUE KEY uk_chart_candidate_zone_person_source (zone_id, person_key, source_url_hash),
  KEY idx_chart_candidates_zone_status (zone_id, status),
  KEY idx_chart_candidates_person_key (person_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_candidate_activities (
  activity_id BIGINT NOT NULL AUTO_INCREMENT,
  candidate_id BIGINT NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  content_summary TEXT NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  discovered_at DATETIME(6) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (activity_id),
  UNIQUE KEY uk_chart_candidate_activity_url (candidate_id, source_url(255)),
  CONSTRAINT fk_chart_candidate_activities_candidate FOREIGN KEY (candidate_id) REFERENCES chart_candidates(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_educators (
  educator_id BIGINT NOT NULL AUTO_INCREMENT,
  person_key VARCHAR(180) NOT NULL,
  name VARCHAR(80) NOT NULL,
  role VARCHAR(40) NOT NULL,
  academy_name VARCHAR(160),
  activity_region VARCHAR(120) NOT NULL,
  zone_id VARCHAR(60) NOT NULL,
  approved_from_candidate_id BIGINT NOT NULL,
  approved_at DATETIME(6) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (educator_id),
  UNIQUE KEY uk_chart_educators_person_key (person_key),
  KEY idx_chart_educators_zone_approved (zone_id, approved_at),
  CONSTRAINT fk_chart_educators_candidate FOREIGN KEY (approved_from_candidate_id) REFERENCES chart_candidates(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
