-- NewStart RAG 벡터 인덱스 (EmbeddingGemma 2, 768차원)
-- 읽기 전용 벡터 저장소. 기존 newstart_knowledge_items 원문은 변경하지 않는다.
CREATE TABLE IF NOT EXISTS newstart_knowledge_embedding (
    knowledge_id BIGINT PRIMARY KEY,
    embedding BLOB NOT NULL COMMENT '768 float32 little-endian, L2 normalized',
    model_version VARCHAR(50) NOT NULL DEFAULT 'embeddinggemma-2' COMMENT '임베딩 모델 버전',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_nke_item FOREIGN KEY (knowledge_id)
        REFERENCES newstart_knowledge_items (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_nke_model ON newstart_knowledge_embedding (model_version);
