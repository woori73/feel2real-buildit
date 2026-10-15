SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 수업 OT 영상은 대표 미디어와 별도 객체다. 대표 미디어의 media_video_id를 재사용하면
-- 한 슬롯을 바꿀 때 다른 슬롯까지 같이 바뀌므로 독립 FK로 저장한다.
-- 학원/회원 삭제 cascade를 깨지 않도록 V934와 같은 ON DELETE SET NULL 정책을 쓴다.
ALTER TABLE lesson_products
  ADD COLUMN ot_video_id BIGINT NULL COMMENT '수업 OT 영상 academy_videos.video_id',
  ADD CONSTRAINT fk_lesson_products_ot_video
    FOREIGN KEY (ot_video_id) REFERENCES academy_videos(video_id)
    ON DELETE SET NULL,
  ADD KEY idx_lesson_products_ot_video (ot_video_id);
