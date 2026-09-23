-- ============================================================
-- V996: choonggi_posts에 youtube_video_id 컬럼 추가
--   · CHOONGGI VLOG 단순화 — 직접 영상 업로드 대신 YouTube 링크를 기본 video source로 쓴다.
--   · 값은 검증된 11자 내외 YouTube video id만 저장한다(URL 원문이 아님).
--   · nullable — 사진만/글만 올리는 기록에는 값이 없다.
--   · 기존 row/컬럼은 건드리지 않는다.
-- ============================================================
ALTER TABLE `choonggi_posts`
  ADD COLUMN `youtube_video_id` VARCHAR(20) NULL AFTER `slug`;
