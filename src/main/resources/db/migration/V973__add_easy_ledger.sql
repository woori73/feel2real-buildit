-- ============================================================
-- V973: Easy 회계·학생원장 v1 (에듀홀릭 회계 프로그램)
--   · easy_businesses: 사업체 (학원/출판사 시드, code UNIQUE — 추가 확장 가능)
--   · easy_students: 회계 내부 학생 원장 (Wiki users와 연결 없음, 독립 운영)
--   · easy_transactions: 수입·지출 (반드시 business 연결)
--   · easy_tuition_payments: 학생별 월 수납 (상태는 앱에서 청구/납부 기준 자동 계산)
--   · 금액은 BIGINT(원) — 부동소수점 사용 금지
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 1. 사업체
-- ============================================================
CREATE TABLE `easy_businesses` (
    `business_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(40) NOT NULL UNIQUE COMMENT '사업체 식별 코드 (하드코딩 대신 code로 참조)',
    `name` VARCHAR(100) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `easy_businesses` (`code`, `name`, `active`, `sort_order`) VALUES
    ('ACADEMY', '압구정에듀홀릭학원', TRUE, 1),
    ('PUBLISHING', '압구정에듀홀릭 출판사', TRUE, 2);

-- ============================================================
-- 2. 학생 원장 (회계 내부 독립 — Wiki users FK 없음)
-- ============================================================
CREATE TABLE `easy_students` (
    `student_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(30) NOT NULL,
    `school` VARCHAR(100) NOT NULL DEFAULT '',
    `grade` TINYINT NOT NULL DEFAULT 0,
    `phone` VARCHAR(20),
    `parent_phone` VARCHAR(20),
    `status` ENUM('ENROLLED', 'WITHDRAWN', 'GRADUATED') NOT NULL DEFAULT 'ENROLLED',
    `enrolled_on` DATE,
    `memo` VARCHAR(500),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_students_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. 수입·지출 거래
-- ============================================================
CREATE TABLE `easy_transactions` (
    `txn_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `occurred_on` DATE NOT NULL,
    `direction` ENUM('INCOME', 'EXPENSE') NOT NULL,
    `amount` BIGINT NOT NULL COMMENT '원 단위 정수 (부동소수점 금지)',
    `category` VARCHAR(50) NOT NULL DEFAULT '기타',
    `counterparty` VARCHAR(100) NOT NULL DEFAULT '',
    `description` VARCHAR(200) NOT NULL DEFAULT '',
    `pay_method` VARCHAR(30) NOT NULL DEFAULT '',
    `memo` VARCHAR(500),
    `receipt_ref` VARCHAR(200) COMMENT '증빙 연결용 참조 (v1은 텍스트만, 파일 업로드 없음)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `chk_easy_txn_amount` CHECK (`amount` >= 0),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    INDEX `idx_easy_txn_business_date` (`business_id`, `occurred_on`),
    INDEX `idx_easy_txn_date` (`occurred_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. 월 수납 (청구/납부 — 상태는 앱 계산 UNPAID/PARTIAL/PAID)
-- ============================================================
CREATE TABLE `easy_tuition_payments` (
    `payment_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `business_id` BIGINT NOT NULL,
    `target_month` CHAR(7) NOT NULL COMMENT 'YYYY-MM',
    `billed_amount` BIGINT NOT NULL,
    `paid_amount` BIGINT NOT NULL DEFAULT 0,
    `paid_on` DATE,
    `pay_method` VARCHAR(30) NOT NULL DEFAULT '',
    `memo` VARCHAR(500),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `chk_easy_pay_billed` CHECK (`billed_amount` >= 0),
    CONSTRAINT `chk_easy_pay_paid` CHECK (`paid_amount` >= 0),
    FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`) ON DELETE CASCADE,
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    UNIQUE KEY `uk_easy_pay_student_business_month` (`student_id`, `business_id`, `target_month`),
    INDEX `idx_easy_pay_month` (`target_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
