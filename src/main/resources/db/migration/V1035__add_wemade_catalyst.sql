-- RENUMBERED: V1025__add_wemade_catalyst.sql -> V1035__add_wemade_catalyst.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1035: 위메이드 2026 Q4 Triple Catalyst (3대 이벤트 + 디지털자산법 입법 추적)
-- wemade_catalyst_events: 3대 촉매 이벤트 정의 및 상태 (DEAL_CLOSE / NCW_LAUNCH / DIGITAL_ASSET_LAW)
-- wemade_policy_tracking: 디지털자산기본법 입법 단계 추적 (국회·금융위 자료 기반)
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- 중복 방지: catalyst_key / policy_key / content_hash UNIQUE.
-- 확정되지 않은 사건은 status로 구분하며 UI에 "미확정" 딱지를 붙인다.

CREATE TABLE IF NOT EXISTS wemade_catalyst_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  catalyst_key VARCHAR(50) NOT NULL COMMENT '고정 키: DEAL_CLOSE|NCW_LAUNCH|DIGITAL_ASSET_LAW',
  name_ko VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED|UNCONFIRMED',
  target_date DATE NULL COMMENT '목표일 (DEAL_CLOSE=2026-10-30, NCW=연내, 법안=미정)',
  target_label VARCHAR(100) NULL COMMENT '목표일 표시 문구 (예: 10/30 거래종결 예정, 연내 출시 목표)',
  summary VARCHAR(2000) NULL,
  key_facts VARCHAR(3000) NULL COMMENT '핵심 수치 JSON 배열 문자열',
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wce_key (catalyst_key),
  UNIQUE KEY uq_wce_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Q4 Triple Catalyst 이벤트';

CREATE TABLE IF NOT EXISTS wemade_policy_tracking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_key VARCHAR(100) NOT NULL COMMENT '고정 키',
  title VARCHAR(500) NOT NULL,
  stage VARCHAR(30) NOT NULL DEFAULT 'TRACKING' COMMENT 'TRACKING|발의|상임위|법사위|본회의|공포|시행',
  stage_date DATE NULL,
  summary VARCHAR(2000) NULL,
  p2e_relevance VARCHAR(1000) NULL COMMENT 'P2E/게임산업법 연관성 메모',
  is_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '확정 사건 여부. 0이면 UI에 미확정 딱지',
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차(국회/금융위), B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wpt_key (policy_key),
  UNIQUE KEY uq_wpt_hash (content_hash),
  INDEX idx_wpt_stage (stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='디지털자산기본법 입법 추적';
