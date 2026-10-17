-- P3: 승인→장부 반영 연결 — commit 추적 컬럼 추가
-- 운영 DB 적용 금지 (PR 병합 후 별도 승인 필요)
-- V1088은 불변 유지, 신규 컬럼만 ALTER로 추가

ALTER TABLE easy_collection_approvals
  ADD COLUMN committed_at DATETIME,
  ADD COLUMN commit_error VARCHAR(1000);
