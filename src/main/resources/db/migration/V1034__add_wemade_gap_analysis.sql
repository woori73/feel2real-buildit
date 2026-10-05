-- RENUMBERED: V1024__add_wemade_gap_analysis.sql -> V1034__add_wemade_gap_analysis.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1034: 위메이드 가격괴리 분석 (9,200억 SPA vs 시총 약 5천억)
-- wemade_gap_factors: 괴리 요인 (경쟁가설 ①~⑥ 매핑, hypothesis_no)
-- wemade_price_events: 2026-06-29 이후 가격 이벤트 타임라인 (OHLCV 실측 기반)
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- 중복 방지: factor_key/content_hash UNIQUE.
-- 수급(외국인/기관 순매수) 데이터는 미확보 — 절대 창작하지 않고 NULL/미표시로 둔다.

CREATE TABLE IF NOT EXISTS wemade_gap_factors (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  factor_key VARCHAR(100) NOT NULL COMMENT '고정 키',
  hypothesis_no TINYINT NOT NULL COMMENT '0=괴리 산식 기준 수치, 1~6=경쟁가설 (6=인수 이해관계자-수급 연관, 미확인 유지)',
  name_ko VARCHAR(200) NOT NULL,
  amount_low_krw BIGINT NULL COMMENT '원 단위. NULL=미산정',
  amount_high_krw BIGINT NULL COMMENT '원 단위. NULL=미산정',
  basis VARCHAR(2000) NULL COMMENT '산정 근거/가정. 가정은 가정으로 명시',
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wgf_key (factor_key),
  UNIQUE KEY uq_wgf_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='가격괴리 요인 (SPA 9,200억 vs 시총)';

CREATE TABLE IF NOT EXISTS wemade_price_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_date DATE NOT NULL COMMENT '거래일 기준',
  title VARCHAR(500) NOT NULL,
  summary VARCHAR(2000) NULL,
  price_change_pct DECIMAL(8,2) NULL COMMENT '전일 종가 대비 등락률(%), 시드 OHLCV 실측',
  close_price INT NULL COMMENT '당일 종가(원), 시드 OHLCV 실측',
  high_price INT NULL COMMENT '당일 고가(원), 시드 OHLCV 실측',
  low_price INT NULL COMMENT '당일 저가(원), 시드 OHLCV 실측',
  volume BIGINT NULL COMMENT '당일 거래량(주), 시드 OHLCV 실측',
  event_type VARCHAR(30) NOT NULL COMMENT 'DEAL|POLICY|NEWS|SURGE|CRASH|VOLUME_SPIKE|MILESTONE',
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wpe_hash (content_hash),
  INDEX idx_wpe_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='6/29 이후 가격 이벤트 타임라인';
