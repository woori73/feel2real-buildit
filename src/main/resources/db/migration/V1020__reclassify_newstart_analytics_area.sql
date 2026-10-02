-- ADMIN 통합 Analytics: NewStart를 독립 서비스로 분리.
-- site_page_visits.path 가 원본 문자열로 저장되어 있으므로 과거 방문 row 도 경로 기준으로 재분류한다.
-- 대상은 /newstart 하위 화면 트래픽만(운영자 콘솔 /admin/newstart** 제외).
-- traffic_kind(봇 제외 기준)·UV(visitor_key distinct)·PV(1 row = 1 PV) 계산 방식과 기존 서비스 집계는 변경하지 않는다.
UPDATE site_page_visits
SET service_area = 'NEWSTART'
WHERE (path = '/newstart' OR path LIKE '/newstart/%')
  AND service_area <> 'NEWSTART';
