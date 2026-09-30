-- ============================================================
-- V1010: EASY bank staging evidence repair (corrective, data only)
--   · bank_import_20260927 계열 배치가 직접 SQL로 적재되면서
--     easy_import_rows.evidence_type에 원천 표기 'BANK_CSV'가 들어갔다.
--   · evidence_type은 EasyEvidenceType enum
--     (TAX_INVOICE/CREDIT_CARD/CASH_RECEIPT/INVOICE/NONE/OTHER) 전용 컬럼이라
--     'BANK_CSV'는 JPA enum 변환을 깨뜨리고 해당 행의 조회·확정을 막는다.
--   · 원천 표기는 batch.source_kind='BANK_CSV' + row.raw_data(JSON src/bank/acct)에
--     그대로 보존되므로, 행 컬럼은 NULL(미확정)로 되돌린다.
--   · 멱등: WHERE로 대상을 한정하므로 재실행해도 같은 결과다.
--   · 행 삭제·금액·날짜·상태 변경 없음. 기존 장부(transaction) 영향 없음.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

UPDATE `easy_import_rows`
   SET `evidence_type` = NULL
 WHERE `evidence_type` = 'BANK_CSV';
