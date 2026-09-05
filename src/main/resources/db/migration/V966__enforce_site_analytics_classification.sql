-- 통합 Analytics V2 3/3: backfill 뒤 제약과 집계 인덱스를 적용한다.
ALTER TABLE site_page_visits
    MODIFY COLUMN service_area VARCHAR(16) NOT NULL,
    MODIFY COLUMN traffic_kind VARCHAR(10) NOT NULL;

CREATE INDEX idx_site_page_visits_area_kind_date
    ON site_page_visits (service_area, traffic_kind, visit_date);
CREATE INDEX idx_site_page_visits_kind_date
    ON site_page_visits (traffic_kind, visit_date);
