-- 전체 공개 커뮤니티(/community): 게시글 + 댓글 + 좋아요.
-- lesson_community와 별개의 공개 공간. 읽기 anyopen, 작성/좋아요 로그인, 삭제 작성자/Admin soft hide.
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE community_posts (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_at DATETIME(6) NULL,
    hidden_by_user_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_community_posts_visible_created (is_visible, created_at, post_id),
    INDEX idx_community_posts_author (author_user_id),
    CONSTRAINT fk_community_posts_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_community_posts_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_comments (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_at DATETIME(6) NULL,
    hidden_by_user_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_community_comments_post_visible_created (post_id, is_visible, created_at),
    CONSTRAINT fk_community_comments_post FOREIGN KEY (post_id) REFERENCES community_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comments_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_community_comments_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MVP는 글을 soft-hide(물리삭제 없음)하므로 아래 ON DELETE CASCADE는 휴면 상태다.
-- 향후 hard-delete 배치 도입 시 좋아요가 자동 정리되도록 미리 둔다.
CREATE TABLE community_post_likes (
    like_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE KEY uk_community_post_likes_post_user (post_id, user_id),
    INDEX idx_community_post_likes_post_created (post_id, created_at),
    INDEX idx_community_post_likes_user (user_id),
    CONSTRAINT fk_community_post_likes_post FOREIGN KEY (post_id) REFERENCES community_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_likes_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
