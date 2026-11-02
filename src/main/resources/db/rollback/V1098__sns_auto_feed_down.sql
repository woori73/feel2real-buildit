-- V1098__sns_auto_feed.sql 되돌리기(수동 실행용). Flyway 경로(db/migration) 밖에 둔다.
-- 실행 전 DB 백업 필수. 실행 후 flyway_schema_history의 V1098 행은 운영 정책상 직접 수정하지 않는다:
-- 다시 올릴 때는 새 번호(V{N})로 같은 테이블을 만든다(docs/WORKFLOW.md §4).
DROP TABLE IF EXISTS sns_generation_logs;
DROP TABLE IF EXISTS sns_posts;
DROP TABLE IF EXISTS sns_automation_rules;
DROP TABLE IF EXISTS sns_reference_images;
DROP TABLE IF EXISTS sns_brand_presets;
DROP TABLE IF EXISTS sns_accounts;
