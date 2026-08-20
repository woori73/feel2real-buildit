-- Existing users receive deterministic nicknames based on login_id.
UPDATE users
SET nickname = login_id
WHERE nickname IS NULL OR TRIM(nickname) = '';
