-- ============================================================
-- V979: Easy 통신비 계정 + 추가 Golden 매핑 3건
--   · 신규 계정: 통신비(EXPENSE_COMMUNICATION).
--     기존 계정 ID·코드·순서 불변 (sort_order 23, V978 22 다음).
--   · Golden 매핑 3건: V978에서 REVIEW로 둔 항목 중
--     사용자 정책으로 확정된 것들만 등록.
--   · 차.음료 global mapping 절대 없음 (이마트24 18건은 별도 처리).
--   · 기존 V978 매핑/계정 불변.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 신규 비용 계정 1개 (멱등: 코드 중복 시 스킵)
INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'EXPENSE_COMMUNICATION', '통신비', 'EXPENSE', 23, TRUE
    FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'EXPENSE_COMMUNICATION');

-- 2. Golden 매핑 3건 (멱등: 동일 pair 존재 시 스킵)
-- 2-1. 전화/휴대폰/인터넷 → 통신비 (신규 계정)
INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '전화/휴대폰/인터넷', '전화/휴대폰/인터넷', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 정책 확정). Golden 매핑 V979.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_COMMUNICATION'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '전화/휴대폰/인터넷');

-- 2-2. 서비스 이용료 → 지급수수료 (기존 EXPENSE_FEE)
INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '서비스 이용료', '서비스 이용료', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 정책 확정). Golden 매핑 V979.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_FEE'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '서비스 이용료');

-- 2-3. 거래처 선물 → 접대비 (기존 EXPENSE_ENTERTAIN from V978)
INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '거래처 선물', '거래처 선물', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 정책 확정). Golden 매핑 V979.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_ENTERTAIN'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '거래처 선물');

-- 3. 차.음료 global mapping 금지 — 의도적 미등록
-- 이마트24 18건은 row-level vendor 조건으로 별도 처리 예정.