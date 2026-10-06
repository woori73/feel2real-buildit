-- V1042: wemade 스키마 정합성 수정 (Hibernate validate 대응)
-- 배경(2026-10-06): V1040이 DB에 phantom 상태로 남아 validation 오류 유발
-- ("Detected resolved migration not applied to database: 1040").
-- V1040 파일을 제거하고 본 V1042로 새로 적용한다.
-- 1) wemade_gap_factors.hypothesis_no: TINYINT -> INT (Entity Integer)
-- 2) wemade_surge_analysis: ret1d/ret3d/ret5d/ret10d 추가 (Entity 네이밍)
-- 모두 NULL 허용 또는 멱등 구문. 기존 데이터 손실 없음.

ALTER TABLE wemade_gap_factors
  MODIFY COLUMN hypothesis_no INT NOT NULL COMMENT '0=괴리 산식 기준 수치, 1~6=경쟁가설 (6=인수 이해관계자-수급 연관, 미확인 유지)';

ALTER TABLE wemade_surge_analysis
  ADD COLUMN ret1d DOUBLE NULL COMMENT '이후 1거래일 수익률(%)',
  ADD COLUMN ret3d DOUBLE NULL COMMENT '이후 3거래일 수익률(%)',
  ADD COLUMN ret5d DOUBLE NULL COMMENT '이후 5거래일 수익률(%)',
  ADD COLUMN ret10d DOUBLE NULL COMMENT '이후 10거래일 수익률(%)';
