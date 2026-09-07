CREATE TABLE midterm_wiki_source_exposures (
  exposure_id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL,
  source_kind VARCHAR(20) NOT NULL,
  source_id BIGINT NOT NULL,
  visible BIT NOT NULL DEFAULT 0,
  featured BIT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  featured_campaign_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN featured = 1 THEN campaign_id ELSE NULL END) STORED,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (exposure_id),
  CONSTRAINT fk_midterm_wiki_exposure_campaign
    FOREIGN KEY (campaign_id) REFERENCES midterm_campaigns(campaign_id) ON DELETE CASCADE,
  CONSTRAINT uq_midterm_wiki_exposure_source
    UNIQUE (campaign_id, source_kind, source_id),
  CONSTRAINT uq_midterm_wiki_exposure_featured
    UNIQUE (featured_campaign_id),
  CONSTRAINT ck_midterm_wiki_exposure_kind
    CHECK (source_kind IN ('school-print', 'final')),
  CONSTRAINT ck_midterm_wiki_exposure_visible_featured
    CHECK (featured = 0 OR visible = 1),
  INDEX idx_midterm_wiki_exposure_public
    (campaign_id, visible, sort_order, exposure_id)
);
