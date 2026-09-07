CREATE TABLE midterm_wiki_source_exposures (
  exposure_id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL,
  source_kind VARCHAR(20) NOT NULL,
  source_id BIGINT NOT NULL,
  visible BIT(1) NOT NULL DEFAULT b'0',
  featured BIT(1) NOT NULL DEFAULT b'0',
  sort_order INT NOT NULL DEFAULT 0,
  featured_campaign_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN featured = b'1' THEN campaign_id ELSE NULL END) STORED,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (exposure_id),
  UNIQUE KEY uk_midterm_wiki_exposure_source (campaign_id, source_kind, source_id),
  UNIQUE KEY uk_midterm_wiki_exposure_featured (featured_campaign_id),
  KEY idx_midterm_wiki_exposure_public (campaign_id, visible, sort_order, exposure_id),
  CONSTRAINT fk_midterm_wiki_exposure_campaign
    FOREIGN KEY (campaign_id) REFERENCES midterm_campaign(campaign_id) ON DELETE CASCADE,
  CONSTRAINT ck_midterm_wiki_exposure_kind
    CHECK (source_kind IN ('school-print', 'final')),
  CONSTRAINT ck_midterm_wiki_exposure_visible_featured
    CHECK (featured = b'0' OR visible = b'1')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
