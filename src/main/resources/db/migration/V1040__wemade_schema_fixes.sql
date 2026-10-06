-- V1040: wemade 스키마 정합성 수정 (Hibernate validate 대응)
-- 배경(2026-10-06): Hibernate 스키마 검증에서 아래 불일치로 앱 기동 실패.
-- 전수 대조(23개 entity vs DDL)로 확정된 것만 수정.
-- 1) wemade_gap_factors.hypothesis_no: DDL TINYINT vs Entity Integer
--    -> "wrong column type ... found [tinyint], but expecting [integer]"
--    -> INT로 변경 (기존 값 0~6은 INT 범위 내, 데이터 손실 없음)
--    (참고: wemade_raw_prices.symbol은 V1031에서 이미 추가됨. 중복 추가 금지.)
-- 2) wemade_surge_analysis.ret1d/ret3d/ret5d/ret10d:
--    DDL(V1036)은 ret_1d/ret_3d/ret_5d/ret_10d, Entity는 ret1d (Spring 네이밍 전략상 ret1d로 매핑)
--    -> Entity가 기대하는 컬럼명으로 추가 (기존 ret_*d 컬럼은 그대로 둠, destructive 변경 없음)

ALTER TABLE wemade_gap_factors
  MODIFY COLUMN hypothesis_no INT NOT NULL COMMENT '0=괴리 산식 기준 수치, 1~6=경쟁가설 (6=인수 이해관계자-수급 연관, 미확인 유지)';

ALTER TABLE wemade_surge_analysis
  ADD COLUMN ret1d DOUBLE NULL COMMENT '이후 1거래일 수익률(%)',
  ADD COLUMN ret3d DOUBLE NULL COMMENT '이후 3거래일 수익률(%)',
  ADD COLUMN ret5d DOUBLE NULL COMMENT '이후 5거래일 수익률(%)',
  ADD COLUMN ret10d DOUBLE NULL COMMENT '이후 10거래일 수익률(%)';
