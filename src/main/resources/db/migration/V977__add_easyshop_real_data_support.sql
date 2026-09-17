-- ============================================================
-- V977: EasyShop 실데이터 검증 반영 (환불·자금분리·대응비용 탐지)
--   · V973~V976 절대 불변 — NULL 허용 컬럼 + ENUM 값 추가만
--   · -330,000원 환불: 방향 전환(INCOME) + is_expense_reversal로 보존
--     (절댓값 변환·누락 없음, supply/vat는 절댓값+플래그로 부호 복원 가능)
--   · 입출금 자금 이동은 P&L에서 제외 (EASYSHOP_FUNDS scope 분리)
--   · 대응 비용 없는 자금 유출은 MISSING_EXPENSE 확인 대상으로만 둔다
--     (임의 분류 없음)
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 환불(역분개) 표시 — NULL이면 일반 거래로 읽는다.
ALTER TABLE `easy_transactions`
    ADD COLUMN `is_expense_reversal` BOOLEAN NULL COMMENT '비용 환불 역분개 (INCOME 방향+비용 차감)',
    ADD INDEX `idx_easy_txn_reversal` (`is_expense_reversal`);

-- 2. staging 환불 표시 — NULL이면 일반 행으로 읽는다.
ALTER TABLE `easy_import_rows`
    ADD COLUMN `reversal` BOOLEAN NULL COMMENT '비용 환불 역분개 행';

-- 3. 검토 종류에 대응 비용 없음 추가 (기존 값 불변).
ALTER TABLE `easy_review_items`
    MODIFY COLUMN `kind` ENUM('UNCLASSIFIED', 'EVIDENCE', 'BUSINESS', 'WITHHOLDING',
        'DUPLICATE_SUSPECT', 'IMPORT_UNCONFIRMED', 'MISSING_EXPENSE')
        NOT NULL COMMENT '검토 종류 (판정 아님)';
