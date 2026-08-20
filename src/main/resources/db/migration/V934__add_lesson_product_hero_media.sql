SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 수업상품 대표 미디어(히어로 슬롯). 이미지 1장 "또는" 영상 1개를 배타적으로(XOR) 건다.
--
-- media_type 컬럼을 두지 않는다: (media_image_key, media_video_id)에서 완전히 파생되므로
-- 컬럼으로 만들면 XOR 검증 지점이 늘고 드리프트 원인이 된다. DTO에서 파생한다.
--
-- 영상은 academy_videos(학원 단위 풀)를 공유하고 여기서는 단방향으로 참조만 한다.
-- academy_videos에 lesson_id를 두는 양방향 참조를 쓰지 않는 이유: 두 컬럼이 서로 다른
-- 동시성 계약(lesson_products는 authoring_revision 낙관적 락, academy_videos는 없음)으로
-- 쓰이는데 화해자가 없어 드리프트가 남는다.
--
-- ON DELETE SET NULL인 이유(RESTRICT면 안 되는 이유):
--   lesson_products(V3)와 academy_videos(V930)가 "둘 다" academies에서 ON DELETE CASCADE다.
--   여기에 RESTRICT FK를 걸면 InnoDB가 형제 CASCADE 간 순서를 보장하지 않아
--   DELETE FROM academies가 errno 1451로 실패한다(= 학원 삭제/회원 탈퇴 파손).
--   UserService.withdraw()는 순수 DB cascade에 의존하고 Academy에 @OneToMany가 없어
--   JPA로 순서를 조정할 seam도 없다.
--   사용자에게 보이는 삭제 차단은 AcademyVideoService.delete()의 참조 검사가 담당한다.
ALTER TABLE lesson_products
  ADD COLUMN media_image_key VARCHAR(500) NULL COMMENT '대표 이미지 FileStorage key. media_video_id와 배타',
  ADD COLUMN media_video_id BIGINT NULL COMMENT '대표 영상 academy_videos.video_id. media_image_key와 배타',
  ADD CONSTRAINT fk_lesson_products_media_video
    FOREIGN KEY (media_video_id) REFERENCES academy_videos(video_id)
    ON DELETE SET NULL,
  ADD KEY idx_lesson_products_media_video (media_video_id);
