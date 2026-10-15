-- BuildIt 시각 편집기 AI 제안(Proposal)·Agent 토큰 전용 테이블.
--
-- 신규 테이블만 추가한다(기존 테이블 변경·데이터 이관 없음 → 기존 홈페이지/초안/게시본/page_settings 무영향).
-- 제안은 초안·게시본에 직접 쓰지 않는다. 사용자가 편집기에서 승인(저장)할 때만 APPLIED가 된다.
-- 설계: docs/BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md
-- MySQL DDL은 암시적 커밋이므로 CREATE TABLE IF NOT EXISTS로 재실행에 안전하게 둔다.

CREATE TABLE IF NOT EXISTS academy_visual_editor_proposal (
  proposal_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL,
  base_revision BIGINT NULL,
  summary VARCHAR(500) NOT NULL,
  changes_json MEDIUMTEXT NOT NULL,
  created_by_kind VARCHAR(20) NOT NULL,
  created_by_ref VARCHAR(100) NULL,
  expires_at TIMESTAMP NOT NULL,
  applied_revision BIGINT NULL,
  applied_by BIGINT NULL,
  applied_at TIMESTAMP NULL,
  decided_at TIMESTAMP NULL,
  stale_reason VARCHAR(200) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (proposal_id),
  CONSTRAINT fk_ve_proposal_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE,
  INDEX idx_ve_proposal_academy_status (academy_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 시각 편집기 AI 제안 (승인 전에는 초안/공개 페이지에 영향 없음)';

CREATE TABLE IF NOT EXISTS academy_visual_editor_agent_token (
  token_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  label VARCHAR(100) NOT NULL,
  scopes VARCHAR(100) NOT NULL,
  created_by BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  last_used_at TIMESTAMP NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token_id),
  CONSTRAINT uq_ve_agent_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_ve_agent_token_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE,
  INDEX idx_ve_agent_token_academy (academy_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='시각 편집기 Agent Bearer 토큰 (SHA-256 해시만 저장, 학원 1개에 바인딩)';
