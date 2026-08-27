-- V950: BuildIt Chart Site tools 조사 지역에 "경기도 성남시 분당구" 등록
--   · chart_regions(V949)는 전국 확장용 조사 지역 레지스트리다. get_next_regions →
--     claim → complete 흐름은 이 테이블의 행만 읽으며, 지역을 늘리는 방법은 행 추가다.
--   · 서울(seoul-gangnam) 다음 실전 조사 대상으로 경기도 성남시 분당구를 추가한다.
--   · 신규 행 1건만 추가한다. 기존 seoul-gangnam 행, chart_candidates 등 다른 Chart
--     데이터는 건드리지 않는다. Java/JS에 지역을 하드코딩하지 않는다.
--   · region_id가 이미 있으면 no-op (재실행·수동 시드 등 환경 차이에도 기존 행 불변).

INSERT INTO chart_regions
  (region_id, province, city_district, status, created_at, updated_at)
VALUES
  ('gyeonggi-seongnam-bundang', '경기도', '성남시 분당구', 'UNRESEARCHED', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE region_id = region_id;
