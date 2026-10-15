-- BuildIt P1: AI 채팅 버전 이력 및 작업 로그 테이블
-- academy_page_versions: 홈페이지 버전 스냅샷 (복원용)
-- academy_ai_sessions: AI 채팅 세션
-- academy_ai_actions: AI 작업 로그 (감사 + 비용 추적)

CREATE TABLE IF NOT EXISTS academy_page_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    builder_draft_json MEDIUMTEXT NOT NULL COMMENT 'builderDraft 전체 스냅샷',
    page_settings_json MEDIUMTEXT COMMENT 'designPreset, sectionOrder 등',
    created_by VARCHAR(100) NOT NULL COMMENT '생성자 (TUTOR ID 또는 AI)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(500) COMMENT '버전 설명 (예: AI 채팅 수정)',
    INDEX idx_academy_version (academy_id, version_no DESC),
    INDEX idx_academy_created (academy_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BuildIt 홈페이지 버전 이력';

CREATE TABLE IF NOT EXISTS academy_ai_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    tutor_id VARCHAR(100) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    message_count INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    INDEX idx_academy_session (academy_id, started_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BuildIt AI 채팅 세션';

CREATE TABLE IF NOT EXISTS academy_ai_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    academy_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL COMMENT 'CHAT, PROPOSE, APPLY, PREVIEW, RESTORE',
    field_changes_json MEDIUMTEXT COMMENT '변경된 필드 목록 (JSON)',
    tokens_used INT DEFAULT 0,
    approved BOOLEAN DEFAULT FALSE COMMENT '고객 승인 여부',
    applied_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_academy_action (academy_id, created_at DESC),
    FOREIGN KEY (session_id) REFERENCES academy_ai_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BuildIt AI 작업 로그';
