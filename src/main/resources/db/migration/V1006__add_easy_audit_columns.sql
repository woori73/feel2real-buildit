-- ============================================================
-- V1006: EASY A1/A2 audit column hotfix (corrective, additive only)
--   · V1002/V1003이 만든 신규 테이블 3건이 Entity(BaseEntity 상속)가 요구하는
--     created_by/updated_by 컬럼 없이 생성되어 fresh MySQL에서
--     Hibernate schema validation이 실패한다:
--       missing column [created_by] in table [easy_bank_account_maps]
--     (easy_card_payment_maps, easy_cutovers 동일 결함)
--   · 이미 병합·적용된 V1002/V1003은 수정하지 않는다 (적용 이력 무결성).
--   · nullable 추가이므로 기존 행/회계 데이터/transaction 내용은 변하지 않는다.
--   · 대상 테이블 외 변경 없음, 데이터 UPDATE 없음.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `easy_bank_account_maps`
    ADD COLUMN `created_by` VARCHAR(50) NULL,
    ADD COLUMN `updated_by` VARCHAR(50) NULL;

ALTER TABLE `easy_card_payment_maps`
    ADD COLUMN `created_by` VARCHAR(50) NULL,
    ADD COLUMN `updated_by` VARCHAR(50) NULL;

ALTER TABLE `easy_cutovers`
    ADD COLUMN `created_by` VARCHAR(50) NULL,
    ADD COLUMN `updated_by` VARCHAR(50) NULL;
