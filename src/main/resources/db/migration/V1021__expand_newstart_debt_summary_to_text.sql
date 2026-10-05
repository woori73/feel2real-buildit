-- ============================================================
-- V1021: NewStart 채무 메모(debt_summary) 1000자 제한 확장
--   · 문제: debt_summary VARCHAR(1000) + 백엔드 SUMMARY_MAX_LENGTH=1000 +
--     프론트 1000자 체크로, 채무 10건 입력 시 "1000자를 넘습니다" 오류 발생
--   · 해결: 컬럼을 TEXT로 확장 (utf8mb4 기준 약 21,000자 수용)
--     백엔드/프론트 제한을 10000자로 상향
--   · 기존 데이터 보존: MODIFY COLUMN은 데이터 유지, NULL 허용 유지
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `newstart_member_debt_status`
    MODIFY COLUMN `debt_summary` TEXT NULL COMMENT '사용자가 직접 정리한 채무 현황 메모(기관명/금액/확인일 등)';
