-- DesignCraft 출판: BaseEntity audit 컬럼 추가 (created_by/updated_by)
-- V1066에서 누락됨. Hibernate schema validation 실패 수정.

ALTER TABLE publishing_jobs ADD COLUMN created_by VARCHAR(50);
ALTER TABLE publishing_jobs ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE publishing_artifacts ADD COLUMN created_by VARCHAR(50);
ALTER TABLE publishing_artifacts ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE publishing_versions ADD COLUMN created_by VARCHAR(50);
ALTER TABLE publishing_versions ADD COLUMN updated_by VARCHAR(50);
