-- BuildIt P1: 버전 무결성 보강 (B4)
--
-- [운영 이력 대응 2026-10-12] 운영 flyway_schema_history에는 version 1053이 이미 다른 파일
-- (V1053__academy_tenant_features_eduholic.sql, 2026-10-10 적용)로 기록되어 있다. 그래서 P1의
-- V1053(academy_page_versions 등 생성)은 운영에서 실행되지 않고 "적용됨"으로 취급되며 테이블이 없다.
-- 이 파일이 ALTER 전에 같은 테이블을 CREATE TABLE IF NOT EXISTS로 보장해, 운영(이미 V1053 슬롯이 다른 파일로
-- 사용됨)과 신규 DB(V1053이 먼저 생성) 양쪽에서 같은 결과가 되게 한다. V1053과 정의가 동일하다.
-- 적용된 V_파일은 불변이므로 V1053은 수정하지 않는다(V1055는 어떤 운영 DB에서도 아직 적용된 적이 없다).

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

-- academy_page_versions: (academy_id, version_no) UNIQUE로 동시 생성 중복 방지
-- P1 테이블에 academies FK 추가 (Academy 삭제 시 고아 데이터 방지)

ALTER TABLE academy_page_versions
    ADD UNIQUE KEY uq_academy_version_no (academy_id, version_no);

ALTER TABLE academy_page_versions
    ADD CONSTRAINT fk_apv_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;

ALTER TABLE academy_ai_sessions
    ADD CONSTRAINT fk_aas_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;

ALTER TABLE academy_ai_idempotency
    ADD CONSTRAINT fk_aai_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;
