-- 통합 Analytics V2 2/3: 기존 방문 row와 visitor_key(S: 포함)는 삭제/재작성하지 않는다.
UPDATE site_page_visits
SET service_area = CASE
    WHEN path = '/' OR path = '/home' THEN 'HOME'
    WHEN path = '/chart' OR path LIKE '/chart/%' THEN 'CHART'
    WHEN path = '/wiki' OR path LIKE '/wiki/%' THEN 'WIKI'
    WHEN path = '/academies' OR path LIKE '/academies/%'
      OR path = '/academy-posts' OR path LIKE '/academy-posts/%'
      OR path = '/subdomain-home' THEN 'WEB'
    WHEN path = '/builder'
      OR path = '/lessons' OR path LIKE '/lessons/%'
      OR path = '/tutor/academy' OR path LIKE '/tutor/academy/%'
      OR path = '/lms' OR path LIKE '/lms/%' THEN 'ACADEMY'
    ELSE 'OTHER'
END,
traffic_kind = 'HUMAN'
WHERE service_area IS NULL OR traffic_kind IS NULL;
