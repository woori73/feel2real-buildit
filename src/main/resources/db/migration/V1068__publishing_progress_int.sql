-- DesignCraft 출판: progress 컬럼 타입 수정 (TINYINT -> INT)
-- Entity의 int 매핑과 일치시킴. Hibernate schema validation 실패 수정.

ALTER TABLE publishing_jobs MODIFY COLUMN progress INT NOT NULL DEFAULT 0;
