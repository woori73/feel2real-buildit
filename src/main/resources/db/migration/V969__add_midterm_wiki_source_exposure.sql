-- V969 최초 배포(#232)는 Production MySQL 8.4 기동 시 실패했다.
--   원인: STORED generated column `featured_campaign_id` 가 `campaign_id` 를 참조하는데,
--         같은 `campaign_id` 에 ON DELETE CASCADE FK 를 걸었다. MySQL 8.4 는
--         "stored generated column 의 base column 을 참조하는 FK 에는 CASCADE /
--          SET NULL / SET DEFAULT 를 쓸 수 없다" 를 강제한다 → CREATE TABLE 자체가 실패.
--   수정: generated column 이 `campaign_id` 를 참조하지 않게 바꾼다. 대신 featured 행에서만
--         b'1' 이 되는 `featured_flag` 를 만들고 (campaign_id, featured_flag) UNIQUE 로
--         "캠페인당 대표 자료 최대 1개" 를 그대로 DB 에서 보장한다. 이제 `campaign_id` 는
--         어떤 generated column 의 base column 도 아니므로 FK ON DELETE CASCADE 가 허용된다.
CREATE TABLE midterm_wiki_source_exposures (
  exposure_id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL,
  source_kind VARCHAR(20) NOT NULL,
  source_id BIGINT NOT NULL,
  visible BIT(1) NOT NULL DEFAULT b'0',
  featured BIT(1) NOT NULL DEFAULT b'0',
  sort_order INT NOT NULL DEFAULT 0,
  featured_flag BIT(1)
    GENERATED ALWAYS AS (CASE WHEN featured = b'1' THEN b'1' ELSE NULL END) STORED,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  PRIMARY KEY (exposure_id),
  UNIQUE KEY uk_midterm_wiki_exposure_source (campaign_id, source_kind, source_id),
  UNIQUE KEY uk_midterm_wiki_exposure_featured (campaign_id, featured_flag),
  KEY idx_midterm_wiki_exposure_public (campaign_id, visible, sort_order, exposure_id),
  CONSTRAINT fk_midterm_wiki_exposure_campaign
    FOREIGN KEY (campaign_id) REFERENCES midterm_campaign(campaign_id) ON DELETE CASCADE,
  CONSTRAINT ck_midterm_wiki_exposure_kind
    CHECK (source_kind IN ('school-print', 'final')),
  CONSTRAINT ck_midterm_wiki_exposure_visible_featured
    CHECK (featured = b'0' OR visible = b'1')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
