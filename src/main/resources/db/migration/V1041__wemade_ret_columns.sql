-- V1041: wemade_surge_analysis ret 컬럼 추가 (Hibernate validate 대응)
-- 배경(2026-10-06): V1040 적용 중 실패.
--   - hypothesis_no INT 변경은 적용됨 (MySQL DDL 자동 커밋, statement 1 성공)
--   - wemade_raw_prices.symbol ADD는 "Duplicate column" 실패 (V1031에서 이미 추가됨)
--   - ret 컬럼 ADD는 실행되지 않음
-- FlywayRepairConfig가 실패한 V1040 기록을 repair한 뒤 본 마이그레이션이 실행된다.
-- Entity(WemadeSurgeAnalysis.ret1d 등, Double)는 Spring 네이밍 전략상 ret1d 컬럼을 기대하나
-- DDL(V1036)에는 ret_1d/ret_3d/ret_5d/ret_10d만 있어 "missing column [ret1d]" 발생.
-- Entity가 기대하는 컬럼명으로 추가한다. 기존 ret_*d 컬럼은 그대로 둔다.
-- hypothesis_no MODIFY는 멱등하므로 안전을 위해 다시 포함한다.

ALTER TABLE wemade_gap_factors
  MODIFY COLUMN hypothesis_no INT NOT NULL COMMENT '0=괴리 산식 기준 수치, 1~6=경쟁가설 (6=인수 이해관계자-수급 연관, 미확인 유지)';

ALTER TABLE wemade_surge_analysis
  ADD COLUMN ret1d DOUBLE NULL COMMENT '이후 1거래일 수익률(%)',
  ADD COLUMN ret3d DOUBLE NULL COMMENT '이후 3거래일 수익률(%)',
  ADD COLUMN ret5d DOUBLE NULL COMMENT '이후 5거래일 수익률(%)',
  ADD COLUMN ret10d DOUBLE NULL COMMENT '이후 10거래일 수익률(%)';
