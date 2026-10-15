-- Add user-facing nickname separately from login_id/name.
-- Nullable first so existing production rows can be backfilled before NOT NULL enforcement.
ALTER TABLE users
    ADD COLUMN nickname VARCHAR(50) NULL AFTER name;
