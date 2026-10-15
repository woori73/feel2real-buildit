-- BuildIt P1: AI 생성 멱등성 키 테이블
-- academy_ai_idempotency: /create/apply 중복 적용 방지
-- (tutor_id, idempotency_key) UNIQUE로 동일 키 재호출 차단

CREATE TABLE IF NOT EXISTS academy_ai_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tutor_id VARCHAR(100) NOT NULL COMMENT 'TUTOR userId',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '클라이언트 생성 UUID',
    academy_id BIGINT NULL COMMENT '생성된 Academy ID (완료 시)',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_idem_tutor_key (tutor_id, idempotency_key),
    INDEX idx_ai_idem_academy (academy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BuildIt AI 생성 멱등성 키';
