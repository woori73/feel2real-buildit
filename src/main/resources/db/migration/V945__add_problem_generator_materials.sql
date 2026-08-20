CREATE TABLE problem_generator_materials (
    material_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uploaded_by BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    visibility ENUM('PRIVATE', 'PUBLIC') NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_problem_generator_material_user
        FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_pg_material_owner_created (uploaded_by, created_at),
    INDEX idx_pg_material_visibility_created (visibility, created_at)
);
