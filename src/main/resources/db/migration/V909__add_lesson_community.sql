CREATE TABLE lesson_community_categories (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_lesson_community_categories_lesson FOREIGN KEY (lesson_id) REFERENCES lesson_products(lesson_id),
    CONSTRAINT uk_lesson_community_categories_lesson_name UNIQUE (lesson_id, name),
    INDEX idx_lesson_community_categories_lesson_visible (lesson_id, is_visible, sort_order)
);

CREATE TABLE lesson_community_posts (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    author_user_id BIGINT NOT NULL,
    post_type VARCHAR(20) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    qna_public BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_at DATETIME(6) NULL,
    hidden_by_user_id BIGINT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_lesson_community_posts_lesson FOREIGN KEY (lesson_id) REFERENCES lesson_products(lesson_id),
    CONSTRAINT fk_lesson_community_posts_category FOREIGN KEY (category_id) REFERENCES lesson_community_categories(category_id),
    CONSTRAINT fk_lesson_community_posts_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_lesson_community_posts_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id),
    INDEX idx_lesson_community_posts_lesson_type_visible (lesson_id, post_type, is_visible, created_at),
    INDEX idx_lesson_community_posts_category_visible (category_id, is_visible, created_at),
    INDEX idx_lesson_community_posts_author (author_user_id)
);

CREATE TABLE lesson_community_comments (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_at DATETIME(6) NULL,
    hidden_by_user_id BIGINT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_lesson_community_comments_post FOREIGN KEY (post_id) REFERENCES lesson_community_posts(post_id),
    CONSTRAINT fk_lesson_community_comments_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_lesson_community_comments_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id),
    INDEX idx_lesson_community_comments_post_visible (post_id, is_visible, created_at),
    INDEX idx_lesson_community_comments_author (author_user_id)
);

CREATE TABLE lesson_community_attachments (
    attachment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    uploader_user_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT fk_lesson_community_attachments_post FOREIGN KEY (post_id) REFERENCES lesson_community_posts(post_id),
    CONSTRAINT fk_lesson_community_attachments_comment FOREIGN KEY (comment_id) REFERENCES lesson_community_comments(comment_id),
    CONSTRAINT fk_lesson_community_attachments_uploader FOREIGN KEY (uploader_user_id) REFERENCES users(user_id),
    CONSTRAINT chk_lesson_community_attachments_parent CHECK (
        (post_id IS NOT NULL AND comment_id IS NULL) OR (post_id IS NULL AND comment_id IS NOT NULL)
    ),
    INDEX idx_lesson_community_attachments_post (post_id, is_visible),
    INDEX idx_lesson_community_attachments_comment (comment_id, is_visible)
);
