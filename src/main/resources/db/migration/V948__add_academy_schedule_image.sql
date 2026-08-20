-- V948: 개설 시간표 섹션의 달력 이미지 슬롯 추가
--   · 자동 생성되는 `.schedule-list`(수업상품에서 파생한 행 목록)는 학원이 실제로 쓰는
--     "달력형 시간표"처럼 보이지 않는다는 요구에서 나왔다. 원장이 직접 만든 달력 이미지를
--     시간표 토글 영역 안에 올릴 수 있게 한다.
--   · 히어로 이미지 슬롯(V940)과 같은 단일 이미지 슬롯이라 업로드/서빙 경로를 그대로 재사용한다
--     (`HeroMediaSlot.SCHEDULE`, `/api/tutor/academy/media/hero/schedule`).
--
-- 기존 row와 무관한 nullable 컬럼 추가라 WORKFLOW.md §4의 3단계 분할 대상이 아니다.

ALTER TABLE `academies`
    ADD COLUMN `schedule_image_storage_key` VARCHAR(500) NULL COMMENT '개설 시간표 달력 이미지 저장 key';
