-- V1027: 위메이드 이상수급 파이프라인 테이블
-- wemade_anomaly_metrics: 개별 지표 원시 수치 보관 (결론부터 내리지 않는다)
-- wemade_member_snapshots: 회원사(거래원) 시간별 스냅샷 (30분 간격, 급등 시 5분 간격)
-- wemade_anomaly_events: 지표 조합 분석 결과 (FACT/ANALYSIS/HYPOTHESIS 구분)
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- KIS 원시 틱 데이터는 DB에 저장하지 않는다 (PRIVATE). 파생 지표만 저장 (INTERNAL).

CREATE TABLE IF NOT EXISTS wemade_anomaly_metrics (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL COMMENT '종목코드: 112040',
  measured_at DATETIME NOT NULL COMMENT '측정 시각',
  metric_type VARCHAR(40) NOT NULL COMMENT 'VOL_SURGE_1M|VOL_SURGE_5M|VOL_SURGE_10M|LARGE_TRADE_COUNT|QUOTE_IMBALANCE|BROKER_TOP1_SELL_RATIO|BROKER_TOP1_BUY_RATIO|FOREIGN_NET_AMT|INSTITUTION_NET_AMT|PRICE_POSITION|BOLLINGER_PCTB|RSI_14|MACD_HIST',
  metric_value DOUBLE NULL COMMENT '지표 원시 수치 (boolean이 아닌 실수)',
  baseline_value DOUBLE NULL COMMENT '비교 기준값 (예: 평상시 평균 거래량)',
  source VARCHAR(20) NULL COMMENT 'KIS_WS|KIS_REST|PYKRX|CALC',
  evidence_level VARCHAR(20) NULL DEFAULT 'ANALYSIS' COMMENT 'FACT|ANALYSIS',
  content_hash VARCHAR(64) NULL UNIQUE COMMENT '중복 방지 해시',
  created_at DATETIME NULL,
  INDEX idx_anomaly_metric_symbol_time (symbol, measured_at),
  INDEX idx_anomaly_metric_type (symbol, metric_type, measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='위메이드 이상수급 지표 (원시 수치)';

CREATE TABLE IF NOT EXISTS wemade_member_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL COMMENT '종목코드: 112040',
  measured_at DATETIME NOT NULL COMMENT '측정 시각',
  broker_name VARCHAR(60) NOT NULL COMMENT '회원사명 (KIS mbcr_name)',
  buy_volume BIGINT NULL COMMENT '매수량 (주)',
  sell_volume BIGINT NULL COMMENT '매도량 (주)',
  net_volume BIGINT NULL COMMENT '순매수량 (주, +순매수/-순매도)',
  buy_amount BIGINT NULL COMMENT '매수금액 (백만원)',
  sell_amount BIGINT NULL COMMENT '매도금액 (백만원)',
  source VARCHAR(20) NULL DEFAULT 'KIS',
  relation_note VARCHAR(200) NULL DEFAULT '연관성 확인되지 않음' COMMENT '직접 증거 없이 변경 금지',
  content_hash VARCHAR(64) NULL UNIQUE COMMENT '중복 방지 해시',
  created_at DATETIME NULL,
  INDEX idx_member_snap_symbol_time (symbol, measured_at),
  INDEX idx_member_snap_broker (symbol, broker_name, measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='위메이드 회원사 시간별 스냅샷';

CREATE TABLE IF NOT EXISTS wemade_anomaly_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL COMMENT '종목코드: 112040',
  detected_at DATETIME NOT NULL COMMENT '탐지 시각',
  analysis_type VARCHAR(40) NOT NULL COMMENT 'VOLUME_SURGE|LARGE_TRADES|QUOTE_IMBALANCE|BROKER_CONCENTRATION|FOREIGN_SELL_RALLY|BOLLINGER_SELL_PRESSURE|POST_SURGE_MOVE',
  summary VARCHAR(1000) NULL COMMENT '분석 요약 (파생 지표 기반, 원시 시세 미포함)',
  evidence_level VARCHAR(20) NOT NULL DEFAULT 'ANALYSIS' COMMENT 'FACT|ANALYSIS|HYPOTHESIS',
  relation_note VARCHAR(500) NULL DEFAULT '연관성 확인되지 않음' COMMENT '직접 증거 없이 변경 금지',
  metric_ids VARCHAR(500) NULL COMMENT '참조 지표 ID 목록 (콤마 구분)',
  similar_past_count INT NULL COMMENT '과거 유사 패턴 매칭 수',
  similar_past_avg_ret5d DOUBLE NULL COMMENT '과거 유사 패턴 이후 평균 5거래일 수익률(%)',
  content_hash VARCHAR(64) NULL UNIQUE COMMENT '중복 방지 해시',
  created_at DATETIME NULL,
  INDEX idx_anomaly_event_symbol_time (symbol, detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='위메이드 이상수급 조합 분석 결과';
