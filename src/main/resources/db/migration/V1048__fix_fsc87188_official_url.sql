-- ============================================================
-- V1048: newstart_official_updates 6월 25일 항목 original_url 정정
--   · V1046에서 등록된 '새출발기금 심사체계 개선 — 재산심사 강화·감면기준 차등화' 항목의
--     original_url이 비공식 형식(https://www.fsc.go.kr/no010101/87188)으로 들어감.
--   · 공식 원문 URL(https://www.fsc.go.kr/comm/getFile?srvcId=BBSTY1&upperNo=87188&fileTy=ATTACH&fileNo=2)로 정정.
--   · dedup_key(DOC:FSC:fsc-87188), title, published_at(2026-06-25)은 이미 올바르므로 변경하지 않음.
--   · 2월 26일 항목(DOC:FSC:fsc-86357)은 유효한 소식이므로 건드리지 않음.
--   · idempotent: WHERE 조건으로 특정 행만 UPDATE하므로 재실행해도 동일 결과.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

UPDATE `newstart_official_updates`
SET `original_url` = 'https://www.fsc.go.kr/comm/getFile?srvcId=BBSTY1&upperNo=87188&fileTy=ATTACH&fileNo=2',
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'DOC:FSC:fsc-87188';
