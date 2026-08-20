-- ============================================================
-- V13: 숙제 부여 시 문제 PDF 없이도 부여 가능 (title+description만)
--   · learning_materials.problem_source_id 를 NOT NULL → NULL 허용으로 완화.
--   · 기존 row는 전부 값이 있으므로 안전 (NULL로 바뀌지 않음).
--   · FK 제약(fk_learning_materials_problem_source)은 유지 — nullable FK.
--     MySQL은 FK 컬럼이 NULL이면 참조 무결성 검증을 건너뛰므로 PDF 없는 숙제도 정상.
--   · 단일 컬럼 nullability 완화는 기존 데이터와 무관 → 1단계로 끝낸다 (3단계 룰 비대상).
-- ============================================================

ALTER TABLE `learning_materials`
  MODIFY COLUMN `problem_source_id` BIGINT NULL
  COMMENT 'sources.source_type=HOMEWORK_PROBLEM. PDF 없이 부여 가능(NULL 허용, V13)';
