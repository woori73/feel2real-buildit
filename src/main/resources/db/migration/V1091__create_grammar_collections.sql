CREATE TABLE grammar_collections (
    collection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    kind ENUM('BASKET','BOOK') NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NULL,
    designcraft_job_id BIGINT NULL,
    publish_options JSON NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_grammar_collection_owner FOREIGN KEY (owner_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_grammar_collection_owner_created (owner_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE grammar_collection_items (
    item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    order_num INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_grammar_collection_item_collection FOREIGN KEY (collection_id) REFERENCES grammar_collections(collection_id) ON DELETE CASCADE,
    CONSTRAINT fk_grammar_collection_item_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    UNIQUE KEY uk_grammar_collection_item (collection_id, question_id),
    INDEX idx_grammar_collection_item_order (collection_id, order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
