-- ============================================================
-- V9001 (dev-seed): Academy 블로그 글에 이미지 4종(투명PNG/WebP/배경PNG/JPEG) 첨부
--   · V9000이 만든 텍스트 글에 이미지 source를 붙이고 본문을 이미지 포함 버전으로 교체한다.
--   · storage_key는 워크트리 루트 기준 상대경로(dev-assets/blog/*.jpg) — 로컬 부팅 시 해석된다.
--     실제 이미지 바이트는 repo에 커밋된 dev-assets/blog/ 파일을 사용한다 (raw_datas는 gitignore라 사용 불가).
--   · source_id를 1~4로 명시 삽입 → 본문 URL과 결정적으로 매칭 (fresh Docker DB 기준).
--
-- ⚠ 운영(prd) 환경에는 적용되지 않는다 (spring.flyway.locations 분기).
-- 목적: 새 워크트리/충돌 복구 후에도 부팅만 하면 이미지 렌더링/서빙 정상 여부를 바로 확인.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @academy_id := (SELECT `academy_id` FROM `academies` WHERE `slug` = 'test-english-academy');
SET @post_id := (SELECT `post_id` FROM `academy_blog_posts`
                 WHERE `academy_id` = @academy_id AND `slug` = '고등영어-내신-1등급-4주-전략');

INSERT INTO `academy_blog_source`
    (`source_id`, `academy_id`, `post_id`, `source_type`, `original_filename`, `stored_filename`,
     `storage_key`, `file_url`, `content_type`, `file_size`, `sort_order`)
VALUES
    (1, @academy_id, @post_id, 'IMAGE', 'blog-transparent-png.jpg', 'blog-transparent-png.jpg', 'dev-assets/blog/blog-transparent-png.jpg', CONCAT('/api/academies/test-english-academy/blog-sources/', 1, '/file'), 'image/jpeg', 23769, 0),
    (2, @academy_id, @post_id, 'IMAGE', 'blog-webp.jpg', 'blog-webp.jpg', 'dev-assets/blog/blog-webp.jpg', CONCAT('/api/academies/test-english-academy/blog-sources/', 2, '/file'), 'image/jpeg', 130858, 1),
    (3, @academy_id, @post_id, 'IMAGE', 'blog-bg-png.jpg', 'blog-bg-png.jpg', 'dev-assets/blog/blog-bg-png.jpg', CONCAT('/api/academies/test-english-academy/blog-sources/', 3, '/file'), 'image/jpeg', 54976, 2),
    (4, @academy_id, @post_id, 'IMAGE', 'blog-jpeg.jpg', 'blog-jpeg.jpg', 'dev-assets/blog/blog-jpeg.jpg', CONCAT('/api/academies/test-english-academy/blog-sources/', 4, '/file'), 'image/jpeg', 337242, 3);

UPDATE `academy_blog_posts`
SET `content_html` = '<h2>이미지 포맷 4종 + 정렬/크기 테스트</h2><p>아래는 업로드한 4가지 이미지 포맷입니다. 모두 서버에서 JPEG로 재인코딩되어 저장됩니다. (dev-seed 자동 주입)</p><h3>1) 투명 배경 PNG → 흰 배경 평탄화 (가운데/중간)</h3><img src="/api/academies/test-english-academy/blog-sources/1/file" alt="투명 PNG" class="blog-img-md" data-align="center"><h3>2) WebP → JPEG (왼쪽/작게)</h3><img src="/api/academies/test-english-academy/blog-sources/2/file" alt="WebP" class="blog-img-sm" data-align="left"><h3>3) 배경 있는 PNG (오른쪽/중간)</h3><img src="/api/academies/test-english-academy/blog-sources/3/file" alt="배경 PNG" class="blog-img-md" data-align="right"><h3>4) JPEG → EXIF 제거 재인코딩 (기본)</h3><img src="/api/academies/test-english-academy/blog-sources/4/file" alt="JPEG">',
    `content_json` = '{"type": "doc", "content": [{"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "이미지 포맷 4종 + 정렬/크기 테스트"}]}, {"type": "paragraph", "content": [{"type": "text", "text": "아래는 업로드한 4가지 이미지 포맷입니다. 모두 서버에서 JPEG로 재인코딩되어 저장됩니다. (dev-seed 자동 주입)"}]}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "1) 투명 배경 PNG → 흰 배경 평탄화 (가운데/중간)"}]}, {"type": "image", "attrs": {"src": "/api/academies/test-english-academy/blog-sources/1/file", "alt": "투명 PNG", "title": null, "class": "blog-img-md", "align": "center"}}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "2) WebP → JPEG (왼쪽/작게)"}]}, {"type": "image", "attrs": {"src": "/api/academies/test-english-academy/blog-sources/2/file", "alt": "WebP", "title": null, "class": "blog-img-sm", "align": "left"}}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "3) 배경 있는 PNG (오른쪽/중간)"}]}, {"type": "image", "attrs": {"src": "/api/academies/test-english-academy/blog-sources/3/file", "alt": "배경 PNG", "title": null, "class": "blog-img-md", "align": "right"}}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "4) JPEG → EXIF 제거 재인코딩 (기본)"}]}, {"type": "image", "attrs": {"src": "/api/academies/test-english-academy/blog-sources/4/file", "alt": "JPEG", "title": null}}]}'
WHERE `post_id` = @post_id;
