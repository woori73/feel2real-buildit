-- 통합 Analytics V2 1/3: 기존 row를 보호하며 분류 컬럼을 nullable로 먼저 추가한다.
ALTER TABLE site_page_visits
    ADD COLUMN service_area VARCHAR(16) NULL AFTER visitor_key,
    ADD COLUMN traffic_kind VARCHAR(10) NULL AFTER service_area;
