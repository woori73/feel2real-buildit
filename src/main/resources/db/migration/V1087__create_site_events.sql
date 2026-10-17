-- Analytics 이벤트 수집(섹션 노출·클릭·서비스 이동·흐름 시작): 신규 테이블만 추가한다. 기존 테이블·데이터 변경 없음.
--
-- 설계 원칙
-- - 페이지 조회(PV, site_page_visits)와 별개의 지표다. 서로 합산하지 않는다.
-- - 개인정보·입력값·문의 내용은 저장하지 않는다. 저장하는 것은 허용 목록(서버 카탈로그)에 있는 이벤트명·대상 id뿐이다.
-- - visitor_key는 기존 Analytics와 같은 기준(로그인 U:{id}, 비로그인 V:{쿠키 UUID})이다.
-- - view_id: 브라우저가 페이지를 열 때마다 만드는 무작위 토큰. 같은 페이지 방문 안의 중복 노출을 막는 데만 쓰며 신원으로 신뢰하지 않는다.
-- - page_path·section은 클라이언트 값이 아니라 서버 카탈로그에서 정한 값이다.
-- - is_internal: 관리자(ADMIN) 계정에서 발생한 내부 테스트 트래픽. 통계에서 제외한다. traffic_kind=BOT도 제외한다.
-- - 보관: 기본 395일(약 13개월) 후 매일 새벽 정리 작업이 삭제한다(app.analytics.events.retention-days).

CREATE TABLE IF NOT EXISTS site_events (
  event_id BIGINT NOT NULL AUTO_INCREMENT,
  event_date DATE NOT NULL,
  occurred_at DATETIME NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  target VARCHAR(64) NOT NULL,
  section VARCHAR(64) NULL,
  page_key VARCHAR(32) NOT NULL,
  view_id VARCHAR(36) NOT NULL,
  seq SMALLINT NOT NULL DEFAULT 0,
  visitor_key VARCHAR(64) NOT NULL,
  traffic_kind VARCHAR(10) NOT NULL,
  is_internal TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (event_id),
  UNIQUE KEY uq_site_events_dedupe (view_id, event_type, target, seq),
  KEY idx_site_events_date_type_target (event_date, event_type, target),
  KEY idx_site_events_date_visitor (event_date, visitor_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Analytics 이벤트(섹션 노출·클릭·서비스 이동·흐름 시작). PV와 별개, 개인정보 없음';
