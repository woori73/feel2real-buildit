CREATE TABLE grammar_question_variants (
    variant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_question_id BIGINT NOT NULL,
    variant_question_id BIGINT NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    change_reason VARCHAR(500) NULL,
    review_note VARCHAR(500) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_grammar_variant_base FOREIGN KEY (base_question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    CONSTRAINT fk_grammar_variant_question FOREIGN KEY (variant_question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    INDEX idx_grammar_variant_base (base_question_id),
    INDEX idx_grammar_variant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
