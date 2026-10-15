-- WikiDailyVisit extends BaseEntity, which maps created_by and updated_by.
-- V960 created wiki_daily_visits without those audit columns, causing
-- Hibernate schema validation to fail at application startup in production.
-- Keep V960 immutable because it may already be recorded by Flyway.

ALTER TABLE wiki_daily_visits
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;
