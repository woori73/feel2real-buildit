-- 공개 커뮤니티를 단순 글/댓글(V914) → StackOverflow식 Q&A로 전환.
-- V914는 이미 stg 등에 적용됐으므로 재작성하지 않고 V915로 in-place 업그레이드(데이터 보존).
-- 질문(community_posts) + 답변(answers 신설) + 댓글(community_comments: post_id → question_id XOR answer_id) + 이미지.
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ── 1) community_posts (= Question): category + Tiptap 본문 ──
ALTER TABLE community_posts
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'FREE' AFTER author_user_id;
ALTER TABLE community_posts
    ADD COLUMN content_json LONGTEXT NULL AFTER title,
    ADD COLUMN content_html LONGTEXT NULL AFTER content_json;
-- 기존 글(있다면) 본문 이관: content → content_html, content_json은 빈 문서.
UPDATE community_posts SET content_html = CONCAT('<p>', content, '</p>') WHERE content_html IS NULL;
UPDATE community_posts SET content_json = '{"type":"doc","content":[]}' WHERE content_json IS NULL;
ALTER TABLE community_posts
    MODIFY COLUMN content_json LONGTEXT NOT NULL,
    MODIFY COLUMN content_html LONGTEXT NOT NULL;
ALTER TABLE community_posts DROP COLUMN content;
ALTER TABLE community_posts
    ADD INDEX idx_community_posts_category_visible_created (category, is_visible, created_at, post_id);

-- ── 2) answers (신설) ──
CREATE TABLE answers (
    answer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content_json LONGTEXT NOT NULL,
    content_html LONGTEXT NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_at DATETIME(6) NULL,
    hidden_by_user_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_answers_question_visible_created (question_id, is_visible, created_at),
    INDEX idx_answers_author (author_user_id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES community_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_answers_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 3) community_images (신설) ──
CREATE TABLE community_images (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uploaded_by BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_community_images_uploader (uploaded_by),
    CONSTRAINT fk_community_images_uploader FOREIGN KEY (uploaded_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 4) community_comments: post_id → question_id XOR answer_id ──
ALTER TABLE community_comments
    ADD COLUMN question_id BIGINT NULL AFTER comment_id,
    ADD COLUMN answer_id BIGINT NULL AFTER question_id;
-- 기존 댓글(있다면)은 모두 질문 댓글이므로 question_id로 이관.
UPDATE community_comments SET question_id = post_id WHERE question_id IS NULL;
-- 기존 post_id FK/인덱스/컬럼 제거.
ALTER TABLE community_comments DROP FOREIGN KEY fk_community_comments_post;
ALTER TABLE community_comments DROP INDEX idx_community_comments_post_visible_created;
ALTER TABLE community_comments DROP COLUMN post_id;
-- XOR 제약 + 신규 FK/인덱스.
ALTER TABLE community_comments
    ADD CONSTRAINT chk_comment_xor CHECK ((question_id IS NULL) <> (answer_id IS NULL)),
    ADD CONSTRAINT fk_community_comments_question FOREIGN KEY (question_id) REFERENCES community_posts(post_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_community_comments_answer FOREIGN KEY (answer_id) REFERENCES answers(answer_id) ON DELETE CASCADE,
    ADD INDEX idx_comments_question_visible (question_id, is_visible, created_at),
    ADD INDEX idx_comments_answer_visible (answer_id, is_visible, created_at);
