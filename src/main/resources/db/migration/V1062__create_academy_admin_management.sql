-- BuildIt 관리자 홈페이지 관리(PR-B): 관리자 전용 작업본 + 감사 기록 (신규 테이블만, 기존 데이터 변경 없음)
--
-- - academy_admin_working_copy: 관리자가 대상 학원에 쓰는 비공개 작업본. 회원 본인의 작업본(academy_working_copy)과
--   분리해, 관리자의 미게시 수정이 회원 화면에 섞이거나 회원 게시에 딸려 나가지 않게 한다. 학원당 1행.
-- - academy_admin_audit: 관리자 작업 감사 기록. 회원 API로는 읽을 수 없고, 학원이 삭제돼도 기록은 남긴다(FK 없음).

CREATE TABLE IF NOT EXISTS academy_admin_working_copy (
  working_copy_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  intro TEXT,
  page_settings_json MEDIUMTEXT,
  base_fingerprint VARCHAR(64) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (working_copy_id),
  CONSTRAINT uq_admin_working_copy_academy UNIQUE (academy_id),
  CONSTRAINT fk_admin_working_copy_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 관리자 전용 비공개 작업본 (회원 작업본과 분리)';

CREATE TABLE IF NOT EXISTS academy_admin_audit (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT NOT NULL,
  actor_login_id VARCHAR(100) NOT NULL,
  academy_id BIGINT NOT NULL,
  owner_user_id BIGINT NULL,
  action VARCHAR(30) NOT NULL,
  working_copy_revision BIGINT NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  summary VARCHAR(500) NULL,
  details_json MEDIUMTEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (audit_id),
  KEY idx_admin_audit_academy (academy_id, audit_id),
  KEY idx_admin_audit_actor (actor_user_id, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 관리자 작업 감사 기록 (ADMIN만 조회, 학원 삭제 후에도 보존)';
