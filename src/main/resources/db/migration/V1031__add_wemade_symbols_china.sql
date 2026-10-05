-- RENUMBERED: V1002__add_wemade_symbols_china.sql -> V1031__add_wemade_symbols_china.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1031: 3종목 주가 + 중국 현지 정보
-- 1) wemade_raw_prices에 종목 구분 컬럼 (복합 PK: symbol + trade_date)
-- 2) wemade_china_disclosures / wemade_china_news 신규
-- destructive 변경 없음.

-- 기존 112040 데이터는 symbol='112040'으로 백필된다.
ALTER TABLE wemade_raw_prices
  ADD COLUMN symbol VARCHAR(20) NOT NULL DEFAULT '112040' FIRST;
ALTER TABLE wemade_raw_prices DROP PRIMARY KEY;
ALTER TABLE wemade_raw_prices ADD PRIMARY KEY (symbol, trade_date);

CREATE TABLE IF NOT EXISTS wemade_china_disclosures (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  announced_at DATE NULL,
  sec_code VARCHAR(20) NOT NULL COMMENT '예: 002517',
  sec_name VARCHAR(100) NULL COMMENT '예: 恺英网络',
  title_cn VARCHAR(1000) NOT NULL COMMENT '중국어 원제목',
  title_kr VARCHAR(1000) NULL COMMENT '한국어 번역 제목',
  summary_kr VARCHAR(3000) NULL COMMENT '한국어 핵심 요약',
  why_matters VARCHAR(2000) NULL COMMENT '위메이드에 왜 중요한가',
  announcement_id VARCHAR(50) NULL COMMENT 'CNINFO announcementId',
  source_url VARCHAR(1000) NULL COMMENT 'CNINFO PDF 링크',
  grade CHAR(1) NOT NULL DEFAULT 'A' COMMENT '공식공시 기본 A',
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wcd_hash (content_hash),
  INDEX idx_wcd_announced (announced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='킹넷 CNINFO 공식 공시 (원문 근거 보존)';

CREATE TABLE IF NOT EXISTS wemade_china_news (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  published_at DATE NULL,
  title_cn VARCHAR(1000) NOT NULL COMMENT '중국어 원제목',
  title_kr VARCHAR(1000) NULL COMMENT '한국어 제목',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  summary_kr VARCHAR(3000) NULL COMMENT '핵심 3~5줄',
  why_matters VARCHAR(2000) NULL COMMENT '위메이드에 왜 중요한가',
  matches_disclosure TINYINT(1) NULL COMMENT '공식공시와 일치 여부',
  grade CHAR(1) NOT NULL DEFAULT 'C' COMMENT 'B=주요경제매체, C=게임전문, D=커뮤니티',
  keywords VARCHAR(500) NULL,
  content_hash CHAR(64) NOT NULL,
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wcn_hash (content_hash),
  INDEX idx_wcn_published (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='중국 뉴스 (원문+번역, 공시와 충돌 시 공시 우선)';
