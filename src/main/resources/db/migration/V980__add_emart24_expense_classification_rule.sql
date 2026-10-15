-- ============================================================
-- V980: 이마트24 편의점 지출 → 소모비 분류 규칙
--   · 목적: counterparty에 "이마트24" 포함된 EXPENSE 거래만 소모품(EXPENSE_SUPPLIES) 자동 분류
--   · 차.음료 global mapping 추가하지 않음 (이마트24만 한정)
--   · 기존 rule/mapping 불변
--   · append-only, idempotent NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 이마트24 편의점 거래 → 소모품 분류 규칙
-- COUNTERPARTY_REGEX: counterparty 컬럼에 '이마트24' 문자열 포함 시 매칭
-- priority=50: V978/V979 Golden mapping(resolveMapping, 1.0) 다음 순위이지만 accountService.classify() fallback(0.9)보다 우선
INSERT INTO `easy_classification_rules`
    (`pattern_type`, `pattern`, `account_id`, `priority`, `amount_min`, `amount_max`, `direction`, `active`)
SELECT 'COUNTERPARTY_REGEX', '이마트24', a.`account_id`, 50, NULL, NULL, 'EXPENSE', TRUE
FROM `easy_accounts` a
WHERE a.`code` = 'EXPENSE_SUPPLIES'
  AND NOT EXISTS (
    SELECT 1 FROM `easy_classification_rules` r
    WHERE r.`pattern_type` = 'COUNTERPARTY_REGEX'
      AND r.`pattern` = '이마트24'
      AND r.`account_id` = a.`account_id`
      AND r.`direction` = 'EXPENSE'
  );