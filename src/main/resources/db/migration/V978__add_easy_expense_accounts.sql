-- ============================================================
-- V978: Easy 비용 계정 3종 + EasyShop Golden 매핑 11건
--   · 신규 계정: 접대비(EXPENSE_ENTERTAIN) / 보험료(EXPENSE_INSURANCE) /
--     여비교통비(EXPENSE_TRAVEL). 기존 계정 ID·코드·순서 불변
--     (sort_order 20~22, 기존 0~19 뒤에 추가).
--   · Golden 매핑: 사용자가 EasyShop 실장부에서 사용한 분류
--     (2024~2026 학원 EXPENSE, EASYSHOP_REAL_LEDGER_ANALYSIS 근거)를
--     easy_account_mappings에 등록. external_code는 EasyShop
--     account_category 원문 그대로 (importer resolveMapping이
--     externalAccount 원문으로 정확 일치 조회하므로 prefix 없음).
--   · 제외(REVIEW 유지, 자동 매핑 미등록): 거래처 선물, 차.음료,
--     차량유류, 잡비, 원재료, 서비스 이용료, 전화/휴대폰/인터넷.
--   · 기존 운영 데이터(SALES 102건, mappingId=1) 불변.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 신규 비용 계정 3개 (멱등: 코드 중복 시 스킵)
INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'EXPENSE_ENTERTAIN', '접대비', 'EXPENSE', 20, TRUE
    FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'EXPENSE_ENTERTAIN');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'EXPENSE_INSURANCE', '보험료', 'EXPENSE', 21, TRUE
    FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'EXPENSE_INSURANCE');

INSERT INTO `easy_accounts` (`code`, `name`, `direction`, `sort_order`, `active`)
    SELECT 'EXPENSE_TRAVEL', '여비교통비', 'EXPENSE', 22, TRUE
    FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM `easy_accounts` WHERE `code` = 'EXPENSE_TRAVEL');

-- 2. Golden 매핑 11건 (멱등: 동일 pair 존재 시 스킵)
-- 2-1. 기존 계정 대상 7건
INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '임차료(월세)', '임차료(월세)', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_RENT'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '임차료(월세)');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '세금(면허세,사업소세등)', '세금(면허세,사업소세등)', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_TAX'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '세금(면허세,사업소세등)');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '건물관리비', '건물관리비', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 압구정스퀘어번영회 공과 포함). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_ADMIN'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '건물관리비');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '교재/도서', '교재/도서', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, PG사 결제 포함). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_PRINT'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '교재/도서');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '잡화/소모품', '잡화/소모품', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_SUPPLIES'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '잡화/소모품');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '일회용품', '일회용품', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_SUPPLIES'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '일회용품');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '기타수수료', '기타수수료', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_FEE'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '기타수수료');

-- 2-2. 신규 계정 대상 4건
INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '접대 식대', '접대 식대', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 김밥천국 등 100% 일관). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_ENTERTAIN'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '접대 식대');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '보험료', '보험료', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026, 교원라이프·신한라이프 일관). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_INSURANCE'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '보험료');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '교통비', '교통비', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_TRAVEL'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '교통비');

INSERT INTO `easy_account_mappings`
    (`account_id`, `external_code`, `external_name`, `standard_code`, `standard_name`, `note`)
    SELECT a.`account_id`, '출장비', '출장비', NULL, '', 'EasyShop 실장부 분류 (학원 2024-2026). Golden 매핑 V978.'
    FROM `easy_accounts` a WHERE a.`code` = 'EXPENSE_TRAVEL'
      AND NOT EXISTS (SELECT 1 FROM `easy_account_mappings` m
        WHERE m.`account_id` = a.`account_id` AND m.`external_code` = '출장비');
