-- 공개 커뮤니티(open-community) 게시글 문서 첨부.
-- 공개 BuildIt Wiki(/wiki)의 변형문제·자료 공유·요청 게시판에서 사용한다.
-- 본문 이미지(community_images, 인라인·재인코딩)와 별개 — 원본 문서를 그대로 보관/다운로드.
-- lesson_community_attachments와 동일한 형태지만 별개 테이블이다.
CREATE TABLE community_post_attachments (
    attachment_id      BIGINT       NOT NULL AUTO_INCREMENT,
    post_id            BIGINT       NOT NULL,
    uploader_user_id   BIGINT       NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT       NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    is_visible         BIT(1)       NOT NULL DEFAULT b'1',
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50),
    PRIMARY KEY (attachment_id),
    INDEX idx_cpa_post (post_id, is_visible, attachment_id),
    CONSTRAINT fk_cpa_post FOREIGN KEY (post_id) REFERENCES community_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_cpa_uploader FOREIGN KEY (uploader_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
