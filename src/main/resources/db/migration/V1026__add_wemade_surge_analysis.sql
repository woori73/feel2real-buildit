-- V1026: 위메이드 급등일 수급 분석 테이블
-- wemade_surge_analysis: 급등일(급등률/고점돌파)마다 외국인·기관 순매도량/금액과
-- 이후 1/3/5/10거래일 수익률을 DB화. '고점 외국인·기관 매도 반복' 가설 검증용.
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- 수급 미확보 시 수급 컬럼은 NULL (0으로 표시하지 않는다).

CREATE TABLE IF NOT EXISTS wemade_surge_analysis (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL COMMENT '종목코드: 112040',
  surge_date DATE NOT NULL COMMENT '급등일',
  surge_type VARCHAR(20) NULL COMMENT 'SURGE_5|SURGE_10|SURGE_20|BREAKOUT_HIGH',
  close_price INT NULL COMMENT '당일 종가',
  change_rate DOUBLE NULL COMMENT '당일 등락률(%)',
  foreign_net_buy BIGINT NULL COMMENT '외국인 순매수 금액(백만원, +순매수/-순매도)',
  institution_net_buy BIGINT NULL COMMENT '기관 순매수 금액(백만원)',
  retail_net_buy BIGINT NULL COMMENT '개인 순매수 금액(백만원)',
  ret_1d DOUBLE NULL COMMENT '이후 1거래일 수익률(%)',
  ret_3d DOUBLE NULL COMMENT '이후 3거래일 수익률(%)',
  ret_5d DOUBLE NULL COMMENT '이후 5거래일 수익률(%)',
  ret_10d DOUBLE NULL COMMENT '이후 10거래일 수익률(%)',
  flow_source VARCHAR(20) NULL COMMENT '수급 데이터 출처: PYKRX|KIS',
  content_hash VARCHAR(64) NULL UNIQUE COMMENT '중복 방지 해시',
  analyzed_at DATETIME NULL,
  INDEX idx_surge_symbol_date (symbol, surge_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='위메이드 급등일 수급 분석';
