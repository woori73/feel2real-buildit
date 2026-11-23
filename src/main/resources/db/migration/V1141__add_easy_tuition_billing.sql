-- ============================================================
-- V1141: F2R 수강료 청구 링크 + 입금 자동 대조 + 결제창 시험 주문 (시험 모드)
--   설계: docs/easy/payment/TUITION_PAYMENT_DESIGN.md
--   · additive only: 새 테이블 3개. 기존 테이블·행 변경 없음.
--   · 카드번호·계좌 비밀번호 등 결제 수단 정보 컬럼 없음.
--     bank_account_no는 학부모에게 안내하는 "학원이 돈을 받는 계좌"다(결제 수단 정보 아님).
--   · 청구 링크 토큰 원문은 저장하지 않는다(SHA-256 해시만).
--   · 수납(easy_tuition_payments)이 삭제되면 딸린 링크·대조·시험 주문도 같이 지운다(CASCADE).
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 청구 링크: 수납 1건을 학부모가 로그인 없이 볼 수 있는 일회용 주소
CREATE TABLE `easy_tuition_bill_links` (
    `link_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payment_id` BIGINT NOT NULL,
    `token_hash` VARCHAR(64) NOT NULL COMMENT '토큰 원문의 SHA-256(hex). 원문은 저장하지 않는다',
    `depositor_code` VARCHAR(8) NOT NULL COMMENT '입금자명에 붙여 달라고 안내하는 숫자 4자리',
    `bank_name` VARCHAR(40) NOT NULL,
    `bank_account_no` VARCHAR(40) NOT NULL COMMENT '학원 수납 계좌(안내용)',
    `bank_account_holder` VARCHAR(40) NOT NULL,
    `status` VARCHAR(20) NOT NULL COMMENT 'ACTIVE / REVOKED (만료는 expires_at으로 계산)',
    `expires_at` DATETIME NOT NULL,
    `revoked_at` DATETIME NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_easy_bill_link_payment` FOREIGN KEY (`payment_id`)
        REFERENCES `easy_tuition_payments` (`payment_id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_easy_bill_link_token` (`token_hash`),
    INDEX `idx_easy_bill_link_payment` (`payment_id`),
    INDEX `idx_easy_bill_link_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 입금 대조: 은행 입금 거래 1건당 최대 1행(txn_id UNIQUE = 같은 입금을 두 번 기록하지 않는 장치)
CREATE TABLE `easy_tuition_deposit_matches` (
    `match_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `txn_id` BIGINT NOT NULL COMMENT 'easy_transactions.txn_id (읽기만 한다. 장부 거래는 고치지 않는다)',
    `payment_id` BIGINT NULL COMMENT '대조된(또는 후보) 수납. 후보를 못 정하면 NULL',
    `amount` BIGINT NOT NULL,
    `occurred_on` DATE NOT NULL,
    `status` VARCHAR(20) NOT NULL COMMENT 'REVIEW / AUTO_CONFIRMED / CONFIRMED / DISMISSED',
    `reason` VARCHAR(30) NOT NULL COMMENT 'CODE_AMOUNT / NAME_AMOUNT / AMOUNT_MISMATCH / AMOUNT_ONLY / MULTIPLE_BILLS / MULTIPLE_DEPOSITS',
    `decided_at` DATETIME NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_easy_deposit_match_payment` FOREIGN KEY (`payment_id`)
        REFERENCES `easy_tuition_payments` (`payment_id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_easy_deposit_match_txn` (`txn_id`),
    INDEX `idx_easy_deposit_match_status` (`status`),
    INDEX `idx_easy_deposit_match_payment` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 결제창 시험 주문: 금액은 서버가 정한 값만 저장한다. 결제 수단 정보는 저장하지 않는다.
CREATE TABLE `easy_tuition_pg_orders` (
    `order_pk` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` VARCHAR(40) NOT NULL COMMENT '서버가 만든 주문번호(추측 불가)',
    `link_id` BIGINT NOT NULL,
    `payment_id` BIGINT NOT NULL,
    `amount` BIGINT NOT NULL COMMENT '주문 시점의 서버 청구 잔액',
    `gateway` VARCHAR(20) NOT NULL COMMENT '결제 대행 구현 코드 (FAKE = 시험, 돈이 움직이지 않음)',
    `status` VARCHAR(20) NOT NULL COMMENT 'READY / PAID / FAILED',
    `payment_key` VARCHAR(200) NULL COMMENT '대행사가 준 결제 식별값(카드정보 아님)',
    `paid_at` DATETIME NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_easy_pg_order_link` FOREIGN KEY (`link_id`)
        REFERENCES `easy_tuition_bill_links` (`link_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_easy_pg_order_payment` FOREIGN KEY (`payment_id`)
        REFERENCES `easy_tuition_payments` (`payment_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_easy_pg_order_amount` CHECK (`amount` > 0),
    UNIQUE KEY `uk_easy_pg_order_id` (`order_id`),
    UNIQUE KEY `uk_easy_pg_order_payment_key` (`payment_key`),
    INDEX `idx_easy_pg_order_payment` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
