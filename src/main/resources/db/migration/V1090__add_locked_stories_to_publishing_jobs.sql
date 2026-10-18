-- 영어 지문 분석 학습지: 원문 보호 스토리 ID 목록 (JSON 배열, 예: [20])
ALTER TABLE publishing_jobs
  ADD COLUMN locked_stories TEXT NULL COMMENT '원문 보호 스토리 ID 목록 (JSON 배열)';
