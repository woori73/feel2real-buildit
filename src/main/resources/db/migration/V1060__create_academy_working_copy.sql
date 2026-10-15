-- BuildIt 통합 화면 1단계: 작업본(working copy) 테이블 (신규 테이블 추가만)
--
-- 설계 근거:
-- - 대화 수정은 작업본에만 쓰고, 공개 홈페이지(academies.intro / page_settings)는 '게시' 시에만 바뀐다.
-- - 기존 테이블·데이터는 변경/삭제하지 않는다. 작업본은 학원당 1행(UNIQUE), 락 없는 낙관적 revision.
-- - base_fingerprint: 작업본을 만든/마지막으로 맞춘 시점의 공개 내용 지문. 게시 시 공개 내용이 그 사이
--   바뀌었으면(예: 기존 빌더에서 저장) 409로 알린다.
-- - 시각 편집기(GrapesJS) 계층은 기존 academy_visual_editor_draft/published를 그대로 쓴다(중복 저장 없음).

CREATE TABLE IF NOT EXISTS academy_working_copy (
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
  CONSTRAINT uq_working_copy_academy UNIQUE (academy_id),
  CONSTRAINT fk_working_copy_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 통합 화면 작업본 (비공개, 본인만 접근)';
