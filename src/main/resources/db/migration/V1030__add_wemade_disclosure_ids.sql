-- RENUMBERED: V1001__add_wemade_disclosure_ids.sql -> V1030__add_wemade_disclosure_ids.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1030: wemade_disclosures 식별자 3중 매핑
-- 정정 반영: rcno 49601334/49763361은 KOSCOM 문서번호. DART 공식 식별자는 rcpNo.
-- 원문 재조회를 위해 KOSCOM id <-> DART rcpNo <-> dcmNo를 함께 저장한다.
-- (기존 컬럼 변경 없음, 추가만. destructive 변경 없음.)

ALTER TABLE wemade_disclosures
  ADD COLUMN rcp_no VARCHAR(30) NULL COMMENT 'DART 접수번호 (예: 20260630900888)' AFTER disclosure_no,
  ADD COLUMN dcm_no VARCHAR(30) NULL COMMENT 'DART 문서번호 (원문 viewer용)' AFTER rcp_no,
  ADD COLUMN koscom_doc_no VARCHAR(30) NULL COMMENT 'KOSCOM 문서번호 (예: 49601334)' AFTER dcm_no,
  ADD INDEX idx_wd_rcp (rcp_no);
