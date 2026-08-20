SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE academy_blog_post_likes (
    like_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE KEY uk_academy_blog_post_likes_post_user (post_id, user_id),
    INDEX idx_academy_blog_post_likes_post_created (post_id, created_at),
    INDEX idx_academy_blog_post_likes_user (user_id),
    CONSTRAINT fk_academy_blog_post_likes_post FOREIGN KEY (post_id) REFERENCES academy_blog_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_academy_blog_post_likes_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
