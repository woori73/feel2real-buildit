-- ============================================================
-- V975: Easy AI 자동장부 기반 (경영 대시보드·검토큐·원천징수·마감)
--   · 신규 테이블만 추가 + easy_transactions에 NULL 허용 컬럼만 추가
--     → V973/V974 데이터·API 완전 호환 (backward compatible)
--   · 세법 자동판정 없음 — "확인 필요" 상태만 만든다
--   · 중복 자동합치기 없음 — fingerprint는 의심 탐지용으로만 쓴다
--   · 종소세 계산 없음 — 개인 귀속연도 합산 구조는 문서로만 준비
--
-- 설계 아이디어 출처 (코드 복사 없음):
--   · kimlawtech/korean-jangbu-for (Apache-2.0) — 거래 표준화,
--     source/source_ref, 중복 제거 식별자, rule 우선·LLM fallback이 아닌
--     rule 우선·사용자 확인 구조, 내부↔표준계정 이중 구조 발상
--   · aivance-kr/AICassa — 공급/부가 분리·증빙유형 발상 (V974에서 반영)
--   · sebae1/SBS — 적격증빙 분류 발상 (V974에서 반영)
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- 1. easy_transactions 확장 — 전부 NULL 허용 (기존 행 영향 없음)
--   · source_kind/source_ref/fingerprint: 여러 source에서 들어오는
--     동일 경제거래의 중복 의심 탐지용 (자동합치기 없음)
--   · evidence_review: 증빙 검토 상태 (세법 판정 아님)
--   · withholding_review: 원천징수 검토 상태 (신고 기능 아님)
-- ============================================================
ALTER TABLE `easy_transactions`
    ADD COLUMN `source_kind` VARCHAR(20) NULL COMMENT '거래 출처 (BANK_CSV/CARD/MANUAL 등, 수집경로 추적용)',
    ADD COLUMN `source_ref` VARCHAR(200) NULL COMMENT '원본 참조 (파일명+행번호 등)',
    ADD COLUMN `fingerprint` VARCHAR(64) NULL COMMENT '중복 의심 탐지용 SHA-256 해시 (자동합치기 없음)',
    ADD COLUMN `evidence_review` VARCHAR(20) NULL COMMENT '증빙 검토 (UNKNOWN/NEEDS_REVIEW/VERIFIED/MISSING/NOT_REQUIRED)',
    ADD COLUMN `withholding_review` VARCHAR(20) NULL COMMENT '원천징수 검토 (NONE/NEEDS_REVIEW/CHECKED)',
    ADD INDEX `idx_easy_txn_fingerprint` (`fingerprint`),
    ADD INDEX `idx_easy_txn_evidence_review` (`evidence_review`),
    ADD INDEX `idx_easy_txn_withholding_review` (`withholding_review`);

-- ============================================================
-- 2. AI 장부 할 일 큐 — 계산 결과를 저장하는 검토 작업대
--   · AI 판정이 아니라 "확인 필요" 목록이다
--   · txn/import_row/batch 중 하나 이상에 연결된다 (모두 NULL 불가)
--   · 사용자 확인(resolve/ignore)으로만 닫힌다
-- ============================================================
CREATE TABLE `easy_review_items` (
    `review_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `kind` ENUM('UNCLASSIFIED', 'EVIDENCE', 'BUSINESS', 'WITHHOLDING', 'DUPLICATE_SUSPECT', 'IMPORT_UNCONFIRMED') NOT NULL COMMENT '검토 종류 (판정 아님)',
    `status` ENUM('OPEN', 'RESOLVED', 'IGNORED') NOT NULL DEFAULT 'OPEN',
    `txn_id` BIGINT NULL COMMENT '연결 거래 (없으면 NULL)',
    `import_row_id` BIGINT NULL COMMENT '연결 staging 행 (없으면 NULL)',
    `batch_id` BIGINT NULL COMMENT '연결 import 배치 (없으면 NULL)',
    `title` VARCHAR(200) NOT NULL DEFAULT '',
    `detail` VARCHAR(500) NULL,
    `amount` BIGINT NULL,
    `occurred_on` DATE NULL,
    `resolution` VARCHAR(500) NULL COMMENT '사용자 확인 내용',
    `resolved_by` VARCHAR(50) NULL,
    `resolved_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    FOREIGN KEY (`txn_id`) REFERENCES `easy_transactions` (`txn_id`) ON DELETE CASCADE,
    FOREIGN KEY (`import_row_id`) REFERENCES `easy_import_rows` (`row_id`) ON DELETE CASCADE,
    FOREIGN KEY (`batch_id`) REFERENCES `easy_import_batches` (`batch_id`) ON DELETE CASCADE,
    INDEX `idx_easy_review_business_status` (`business_id`, `status`),
    INDEX `idx_easy_review_kind_status` (`kind`, `status`),
    INDEX `idx_easy_review_txn` (`txn_id`),
    INDEX `idx_easy_review_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. 원천징수 검토 기록 — 최소 기반 (신고 기능 아님)
--   · 3.3% 인적용역 같은 거래를 일반 비용과 구분해
--     "원천징수 검토 필요"로 관리한다
--   · 지급총액/실지급액/원천징수액/지급일/관련거래/신고확인 상태를
--     한 곳에 연결한다
-- ============================================================
CREATE TABLE `easy_withholding_records` (
    `record_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `payee` VARCHAR(100) NOT NULL COMMENT '지급 대상',
    `gross_amount` BIGINT NOT NULL COMMENT '지급총액 (원)',
    `net_amount` BIGINT NULL COMMENT '실제 지급액 (원)',
    `withholding_amount` BIGINT NULL COMMENT '원천징수액 (원)',
    `paid_on` DATE NULL COMMENT '지급일',
    `txn_id` BIGINT NULL COMMENT '관련 거래',
    `filing_checked` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '원천징수 신고 확인',
    `statement_checked` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '지급명세서 확인',
    `memo` VARCHAR(500) NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `chk_easy_wh_gross` CHECK (`gross_amount` >= 0),
    CONSTRAINT `chk_easy_wh_net` CHECK (`net_amount` IS NULL OR `net_amount` >= 0),
    CONSTRAINT `chk_easy_wh_tax` CHECK (`withholding_amount` IS NULL OR `withholding_amount` >= 0),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    FOREIGN KEY (`txn_id`) REFERENCES `easy_transactions` (`txn_id`) ON DELETE SET NULL,
    INDEX `idx_easy_wh_business_paid` (`business_id`, `paid_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. 연도 마감 — 단순 상태 + 재마감 가능 (복잡 UI 없음)
--   · 마감 전 검토 항목 확인용
--   · 마감 후에도 해제→수정→재검증→재마감 가능
-- ============================================================
CREATE TABLE `easy_annual_closings` (
    `closing_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `fiscal_year` INT NOT NULL COMMENT '귀속연도',
    `status` ENUM('OPEN', 'REVIEW', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    `checklist_snapshot` TEXT NULL COMMENT '마감 시점 검토 현황 스냅샷(JSON)',
    `note` VARCHAR(500) NULL,
    `closed_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    UNIQUE KEY `uk_easy_closing_business_year` (`business_id`, `fiscal_year`),
    INDEX `idx_easy_closing_year_status` (`fiscal_year`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
