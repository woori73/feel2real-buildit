-- Publishing 작업 재시도 횟수 추적 (무한 반복 방지)
ALTER TABLE publishing_jobs ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
