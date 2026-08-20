CREATE TABLE problem_generator_question_sets (
    set_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    material_id BIGINT NULL,
    title VARCHAR(180) NOT NULL,
    grammar TEXT NULL,
    prompt_passage LONGTEXT NULL,
    model VARCHAR(80) NULL,
    raw_response_json JSON NULL,
    visibility ENUM('PRIVATE', 'PUBLIC') NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_pg_question_set_owner FOREIGN KEY (owner_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_pg_question_set_material FOREIGN KEY (material_id) REFERENCES problem_generator_materials(material_id) ON DELETE SET NULL,
    INDEX idx_pg_question_set_owner_created (owner_user_id, created_at),
    INDEX idx_pg_question_set_visibility_created (visibility, created_at),
    INDEX idx_pg_question_set_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem_generator_set_questions (
    set_question_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    order_num INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_pg_set_question_set FOREIGN KEY (set_id) REFERENCES problem_generator_question_sets(set_id) ON DELETE CASCADE,
    CONSTRAINT fk_pg_set_question_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    UNIQUE KEY uk_pg_set_question (set_id, question_id),
    INDEX idx_pg_set_question_order (set_id, order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
