CREATE TABLE academy_easy_business_links (
    link_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uk_academy_easy_business UNIQUE (academy_id, business_id),
    CONSTRAINT fk_academy_easy_business_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE,
    CONSTRAINT fk_academy_easy_business_business
        FOREIGN KEY (business_id) REFERENCES easy_businesses (business_id) ON DELETE CASCADE,
    INDEX idx_academy_easy_business_business (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
