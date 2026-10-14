-- DesignCraft 출판: updated_at 컬럼 추가 (publishing_artifacts, publishing_versions)
-- V1066에서 누락됨. Hibernate schema validation 실패 수정.

ALTER TABLE publishing_artifacts ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE publishing_versions ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
