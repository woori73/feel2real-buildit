-- V940: 히어로(V8 시안) 전용 이미지 슬롯 추가
--   · 원장 대표사진 1장 + 대표 교재 표지 3장.
--   · logo/cover와 별개 슬롯이다 — 각 이미지를 개별 관리한다는 방침에 따라 재사용하지 않는다.
--   · 시안이 교재 표지를 3장 고정 배치라 가변 테이블 대신 슬롯 컬럼 3개로 둔다.
--   · 응답 DTO에는 storage key를 싣지 않고 전용 조회 endpoint로만 바이트를 노출한다
--     (logo/cover, lesson hero-image와 동일한 선례).
--
-- 기존 row와 무관한 nullable 컬럼 추가라 WORKFLOW.md §4의 3단계 분할 대상이 아니다.

ALTER TABLE `academies`
    ADD COLUMN `principal_photo_storage_key`  VARCHAR(500) NULL COMMENT '히어로 원장 대표사진 저장 key',
    ADD COLUMN `textbook_cover1_storage_key`  VARCHAR(500) NULL COMMENT '히어로 대표 교재 표지 1 저장 key',
    ADD COLUMN `textbook_cover2_storage_key`  VARCHAR(500) NULL COMMENT '히어로 대표 교재 표지 2 저장 key',
    ADD COLUMN `textbook_cover3_storage_key`  VARCHAR(500) NULL COMMENT '히어로 대표 교재 표지 3 저장 key';
