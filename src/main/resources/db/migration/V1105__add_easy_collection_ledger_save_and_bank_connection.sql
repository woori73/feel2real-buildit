-- ============================================================
-- V1105: EASY 자동수집 — 수집 즉시 중앙 장부 저장 + 은행 연결 설정
--   운영 원칙(docs/operating-principles/EASY_LEDGER_OPERATING_PRINCIPLES.md):
--   수집 → 원본 보존 → 최소 중복 방지 → 중앙 장부 저장 → 자동분류 → 예외 검토
--   · additive only: 기존 컬럼·행 변경/삭제 없음.
--   · 금융 비밀번호·인증서·OTP·API 비밀키 저장 컬럼 없음.
--     provider_config는 기관코드·고객구분·계좌번호 같은 비민감 조회 조건만 담는다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 연결 설정·해제 기록 (해제 = 조회 중단 + 인증 참조 비활성화, 장부·증빙은 그대로)
ALTER TABLE `easy_collection_connections`
    ADD COLUMN `provider_config` VARCHAR(500) NULL
        COMMENT '비민감 조회 조건 JSON(기관코드·고객구분·계좌번호). 비밀번호·인증서·OTP 금지' AFTER `connection_ref`,
    ADD COLUMN `last_error_code` VARCHAR(40) NULL
        COMMENT '마지막 실패 분류(AUTH_REQUIRED 등). 원문 오류·자격증명 저장 금지' AFTER `last_success_at`,
    ADD COLUMN `disconnected_at` TIMESTAMP NULL
        COMMENT '연결 해제 시각. 해제 시 status=DISCONNECTED, connection_ref=NULL' AFTER `last_error_code`;

-- 실행 단위로 "API 수신"과 "중앙 장부 저장 완료"를 구분해 기록한다.
ALTER TABLE `easy_collection_runs`
    ADD COLUMN `fetched_at` TIMESTAMP NULL
        COMMENT '마지막 기관 API 응답 수신 시각(장부 저장 완료와 별개)' AFTER `completed_at`,
    ADD COLUMN `ledger_saved_count` INT NOT NULL DEFAULT 0
        COMMENT '이번 실행으로 중앙 장부(easy_transactions)에 새로 저장된 거래 수' AFTER `error_count`,
    ADD COLUMN `ledger_review_count` INT NOT NULL DEFAULT 0
        COMMENT '저장된 거래 중 미분류·검토 필요 수' AFTER `ledger_saved_count`,
    ADD COLUMN `ledger_classified_count` INT NOT NULL DEFAULT 0
        COMMENT '저장된 거래 중 계정 분류 완료 수' AFTER `ledger_review_count`,
    ADD COLUMN `ledger_saved_through` DATE NULL
        COMMENT '중앙 장부 저장까지 끝난 마지막 조회일(이 날짜까지만 커서가 전진)' AFTER `fetched_at`,
    ADD COLUMN `ledger_saved_at` TIMESTAMP NULL
        COMMENT '마지막 장부 저장 커밋 시각' AFTER `ledger_saved_through`;

-- 취소·부분취소·정산 거래를 원거래와 연결한다(합치거나 덮어쓰지 않는다).
ALTER TABLE `easy_transactions`
    ADD COLUMN `original_txn_id` BIGINT NULL
        COMMENT '취소·부분취소의 원거래 txn_id (원거래는 수정하지 않음)',
    ADD KEY `idx_easy_txn_original` (`original_txn_id`);

-- 첫 실제 연동 대상: CODEF 수시입출 거래내역(은행). 계약·키·검증 전에는 readiness가 UNVERIFIED라
-- 자동 실행되지 않는다(easy.collection.verified-sources에 등록해야 READY).
INSERT INTO `easy_collection_sources` (`code`, `name`, `adapter_type`, `active`)
SELECT 'CODEF_BANK', '은행 입출금(CODEF 수시입출 거래내역)', 'RELAY_API', TRUE
WHERE NOT EXISTS (SELECT 1 FROM `easy_collection_sources` WHERE `code` = 'CODEF_BANK');
