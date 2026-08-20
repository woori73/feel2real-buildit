SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE academy_blog_posts (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    summary VARCHAR(500),
    content_json LONGTEXT NOT NULL,
    content_html LONGTEXT NOT NULL,
    meta_title VARCHAR(200),
    meta_description VARCHAR(300),
    status ENUM('DRAFT', 'PUBLISHED') NOT NULL DEFAULT 'DRAFT',
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE KEY uk_academy_blog_posts_academy_slug (academy_id, slug),
    INDEX idx_academy_blog_posts_public (academy_id, status, is_visible, published_at),
    CONSTRAINT fk_academy_blog_posts_academy FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academy_blog_source (
    source_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    source_type ENUM('IMAGE') NOT NULL DEFAULT 'IMAGE',
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255),
    storage_key VARCHAR(500) NOT NULL,
    file_url VARCHAR(500),
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_academy_blog_source_post (post_id, sort_order),
    CONSTRAINT fk_academy_blog_source_academy FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE CASCADE,
    CONSTRAINT fk_academy_blog_source_post FOREIGN KEY (post_id) REFERENCES academy_blog_posts(post_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
