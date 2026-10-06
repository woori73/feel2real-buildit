-- RENUMBERED: V1028__add_wemade_exchange.sql -> V1038__add_wemade_exchange.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1038: 위메이드 KRX/NXT 시장 분리 컬럼 추가
-- 원칙: 기존 컬럼/데이터 변경 없음. 신규 컬럼만 추가 (nullable, 기본값 KRX).
-- KIS 원시 틱 데이터는 DB에 저장하지 않는다 (PRIVATE). 파생 지표만 저장 (INTERNAL).
-- FIX(2026-10-06): MySQL은 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS 구문을
-- 지원하지 않음 (Error 1064). V1038은 운영 DB에 적용된 적 없으므로(구문 오류로 실패, history 미기록)
-- 일반 구문으로 교체. 이미 적용된 V1029~V1037은 수정하지 않음.

ALTER TABLE wemade_anomaly_metrics
  ADD COLUMN exchange VARCHAR(10) NULL DEFAULT 'KRX' COMMENT 'KRX|NXT|UNIFIED',
  ADD COLUMN session VARCHAR(10) NULL COMMENT 'PRE|REGULAR|AFTER|CLOSE (KIS MARKET_CLS_CODE 기준)';

ALTER TABLE wemade_member_snapshots
  ADD COLUMN exchange VARCHAR(10) NULL DEFAULT 'KRX' COMMENT 'KRX|NXT|UNIFIED',
  ADD COLUMN session VARCHAR(10) NULL COMMENT 'PRE|REGULAR|AFTER|CLOSE (KIS MARKET_CLS_CODE 기준)';

ALTER TABLE wemade_anomaly_events
  ADD COLUMN exchange VARCHAR(10) NULL COMMENT 'KRX|NXT|UNIFIED|MULTI (복수 시장 관련 시)';

-- NXT 지표 조회를 위한 인덱스
CREATE INDEX idx_anomaly_metric_exchange ON wemade_anomaly_metrics (symbol, exchange, measured_at);
CREATE INDEX idx_member_snap_exchange ON wemade_member_snapshots (symbol, exchange, measured_at);
