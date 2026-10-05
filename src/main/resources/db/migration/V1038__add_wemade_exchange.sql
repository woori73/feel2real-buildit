-- RENUMBERED: V1028__add_wemade_exchange.sql -> V1038__add_wemade_exchange.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1038: 위메이드 KRX/NXT 시장 분리 컬럼 추가
-- 원칙: 기존 컬럼/데이터 변경 없음. 신규 컬럼만 추가 (nullable, 기본값 KRX).
-- KIS 원시 틱 데이터는 DB에 저장하지 않는다 (PRIVATE). 파생 지표만 저장 (INTERNAL).

ALTER TABLE wemade_anomaly_metrics
  ADD COLUMN IF NOT EXISTS exchange VARCHAR(10) NULL DEFAULT 'KRX' COMMENT 'KRX|NXT|UNIFIED',
  ADD COLUMN IF NOT EXISTS session VARCHAR(10) NULL COMMENT 'PRE|REGULAR|AFTER|CLOSE (KIS MARKET_CLS_CODE 기준)';

ALTER TABLE wemade_member_snapshots
  ADD COLUMN IF NOT EXISTS exchange VARCHAR(10) NULL DEFAULT 'KRX' COMMENT 'KRX|NXT|UNIFIED',
  ADD COLUMN IF NOT EXISTS session VARCHAR(10) NULL COMMENT 'PRE|REGULAR|AFTER|CLOSE (KIS MARKET_CLS_CODE 기준)';

ALTER TABLE wemade_anomaly_events
  ADD COLUMN IF NOT EXISTS exchange VARCHAR(10) NULL COMMENT 'KRX|NXT|UNIFIED|MULTI (복수 시장 관련 시)';

-- NXT 지표 조회를 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_anomaly_metric_exchange ON wemade_anomaly_metrics (symbol, exchange, measured_at);
CREATE INDEX IF NOT EXISTS idx_member_snap_exchange ON wemade_member_snapshots (symbol, exchange, measured_at);
