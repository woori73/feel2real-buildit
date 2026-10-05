-- Wemade 개인주주 분석 서비스 MVP 테이블
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- 파이프라인: RAW DATA -> FACT CHECK -> EVENT -> PRICE/FLOW -> ANALYSIS -> UI
-- 중복 방지: 모든 raw 테이블에 content_hash UNIQUE.

CREATE TABLE IF NOT EXISTS wemade_collection_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source VARCHAR(50) NOT NULL COMMENT 'PRICE, FLOW, DISCLOSURE, GAME, FACTCHECK',
  started_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  fetched INT NOT NULL DEFAULT 0,
  accepted INT NOT NULL DEFAULT 0,
  duplicate INT NOT NULL DEFAULT 0,
  error INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
  message VARCHAR(500) NULL,
  INDEX idx_wcr_source_started (source, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='수집 실행 기록 (source별 fetched/accepted/duplicate/error)';

CREATE TABLE IF NOT EXISTS wemade_raw_prices (
  trade_date DATE NOT NULL PRIMARY KEY,
  open_price INT NOT NULL,
  high_price INT NOT NULL,
  low_price INT NOT NULL,
  close_price INT NOT NULL,
  volume BIGINT NOT NULL,
  source VARCHAR(50) NOT NULL DEFAULT 'NAVER_FCHART',
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wrp_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일봉 RAW (지표는 서버에서 deterministic 계산)';

CREATE TABLE IF NOT EXISTS wemade_investor_flows (
  trade_date DATE NOT NULL,
  investor_type VARCHAR(20) NOT NULL COMMENT 'FOREIGN, INSTITUTION, RETAIL',
  net_buy_amount BIGINT NULL COMMENT '순매수 금액(백만원, +매수/-매도)',
  buy_amount BIGINT NULL,
  sell_amount BIGINT NULL,
  source VARCHAR(50) NOT NULL DEFAULT 'NAVER',
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (trade_date, investor_type),
  UNIQUE KEY uq_wif_hash (content_hash),
  INDEX idx_wif_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='투자자별 수급 RAW (자체 누적 DB)';

CREATE TABLE IF NOT EXISTS wemade_disclosures (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  disclosed_at DATE NULL,
  title VARCHAR(500) NOT NULL,
  disclosure_no VARCHAR(100) NULL COMMENT 'rcno',
  source VARCHAR(50) NOT NULL DEFAULT 'DART_LIST',
  source_url VARCHAR(1000) NULL,
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wd_hash (content_hash),
  INDEX idx_wd_disclosed (disclosed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공시 목록 RAW (원문은 DART API 키 필요 - BLOCKED)';

CREATE TABLE IF NOT EXISTS wemade_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_date DATE NOT NULL,
  category VARCHAR(50) NOT NULL COMMENT 'ACQUISITION, PANHO, GAME, EARNINGS, DISCLOSURE, PRICE, OTHER',
  title VARCHAR(500) NOT NULL,
  summary VARCHAR(2000) NULL,
  source VARCHAR(50) NOT NULL COMMENT 'OFFICIAL, PRESS, COMMUNITY',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  fact_grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인',
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_we_hash (content_hash),
  INDEX idx_we_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주가 결합 이벤트';

CREATE TABLE IF NOT EXISTS wemade_fact_checks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  claim_key VARCHAR(100) NOT NULL,
  claim VARCHAR(500) NOT NULL,
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인',
  verdict VARCHAR(2000) NULL COMMENT '판정 내용',
  basis VARCHAR(2000) NULL COMMENT '근거',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wfc_claim (claim_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주장별 독립 Fact Check (등급제)';

CREATE TABLE IF NOT EXISTS wemade_community_posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  posted_at DATETIME NULL,
  title VARCHAR(500) NULL,
  excerpt VARCHAR(2000) NULL,
  view_count INT NULL,
  like_count INT NULL,
  holder_verified TINYINT(1) NULL COMMENT '보유인증 여부',
  mentions VARCHAR(500) NULL COMMENT '언급 종목/게임/공시',
  needs_fact_check TINYINT(1) NOT NULL DEFAULT 0,
  fact_check_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/QUEUED/CHECKED',
  source VARCHAR(100) NOT NULL DEFAULT 'MANUAL',
  source_url VARCHAR(1000) NULL,
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wcp_hash (content_hash),
  INDEX idx_wcp_posted (posted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주주 정보(커뮤니티) - 사실 데이터와 분리, 사실로 승격 금지';

CREATE TABLE IF NOT EXISTS wemade_game_tracking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tracked_at DATE NOT NULL,
  game_name VARCHAR(200) NOT NULL COMMENT '예: Night Crows W (중국명 夜鸦)',
  item VARCHAR(100) NOT NULL COMMENT 'PANHO, PUBLISHER, PREREG, CBT, LAUNCH, RANK_APPSTORE, RANK_GP, RANK_REVENUE, ANNOUNCE',
  detail VARCHAR(2000) NULL,
  source_type VARCHAR(20) NOT NULL COMMENT 'OFFICIAL, PRESS, COMMUNITY - 반드시 분리 표시',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wgt_hash (content_hash),
  INDEX idx_wgt_game_item (game_name, item, tracked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Night Crows W / 중국 Tracker';
