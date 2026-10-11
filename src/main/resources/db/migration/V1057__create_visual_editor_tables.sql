-- BuildIt 시각 편집기 초안·게시본 전용 테이블
--
-- 설계 근거:
-- - 기존 P0/P1 builderDraft와 완전히 분리 (데이터 혼재 방지)
-- - 초안과 게시본을 별도 테이블로 분리 (자동 게시 방지)
-- - P1 스냅샷/D3 버전 번호와 연결하지 않음 (독립적)
-- - revision 컬럼으로 낙관적 잠금 (동시 수정 충돌 시 409)
--
-- 주의: 이 마이그레이션은 격리 브랜치에서만 작성되며,
-- 운영 DB에는 별도 승인 없이 실행하지 않는다.

CREATE TABLE IF NOT EXISTS academy_visual_editor_draft (
  draft_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  html MEDIUMTEXT,
  css TEXT,
  project_data MEDIUMTEXT,
  created_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (draft_id),
  CONSTRAINT uq_visual_draft_academy UNIQUE (academy_id),
  CONSTRAINT fk_visual_draft_academy FOREIGN KEY (academy_id)
    REFERENCES academy (academy_id) ON DELETE CASCADE,
  INDEX idx_visual_draft_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 시각 편집기 초안 (비공개, 본인만 접근)';

CREATE TABLE IF NOT EXISTS academy_visual_editor_published (
  published_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  html MEDIUMTEXT,
  css TEXT,
  published_by VARCHAR(100) NOT NULL,
  published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (published_id),
  CONSTRAINT uq_visual_published_academy UNIQUE (academy_id),
  CONSTRAINT fk_visual_published_academy FOREIGN KEY (academy_id)
    REFERENCES academy (academy_id) ON DELETE CASCADE,
  INDEX idx_visual_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 시각 편집기 게시본 (공개 홈페이지에 반영)';
