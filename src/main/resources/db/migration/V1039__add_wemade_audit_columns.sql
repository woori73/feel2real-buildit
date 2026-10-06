-- V1039: wemade 테이블 audit 컬럼 보완
-- 배경(2026-10-06): Hibernate ddl-auto=validate에서 BaseEntity(@CreatedDate/@LastModifiedDate/
-- @CreatedBy/@LastModifiedBy) 상속 엔티티의 created_by 컬럼 부재로 SchemaManagementException 발생,
-- 앱 기동 실패 (table: wemade_catalyst_events).
-- 각 테이블의 기존 컬럼 상태를 확인 후 누락분만 ADD한다. 모두 NULL 허용 (기존 데이터 호환).
-- MySQL은 ADD COLUMN IF NOT EXISTS를 지원하지 않으므로 일반 구문 사용.
-- BaseEntity를 상속하지 않는 7개 테이블
-- (wemade_collection_runs, wemade_raw_prices, wemade_investor_flows, wemade_surge_analysis,
--  wemade_anomaly_metrics, wemade_member_snapshots, wemade_anomaly_events)은 대상에서 제외.

-- V1029: wemade_events (created_at 존재)
ALTER TABLE wemade_events
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1029: audit 컬럼 없음
ALTER TABLE wemade_fact_checks
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_disclosures
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_community_posts
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_game_tracking
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1031: audit 컬럼 없음
ALTER TABLE wemade_china_disclosures
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_china_news
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1032: audit 컬럼 없음
ALTER TABLE wemade_valuation_components
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_valuation_scenarios
  ADD COLUMN created_at DATETIME NULL,
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1033: created_at만 존재
ALTER TABLE wemade_ownership_nodes
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_persons
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_ownership_edges
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1034: created_at만 존재
ALTER TABLE wemade_gap_factors
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_price_events
  ADD COLUMN updated_at DATETIME NULL,
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

-- V1035: created_at, updated_at 존재
ALTER TABLE wemade_catalyst_events
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;

ALTER TABLE wemade_policy_tracking
  ADD COLUMN created_by VARCHAR(50) NULL,
  ADD COLUMN updated_by VARCHAR(50) NULL;
