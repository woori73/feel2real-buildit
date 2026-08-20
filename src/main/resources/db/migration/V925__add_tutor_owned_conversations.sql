-- Tutor-owned conversations: one conversations aggregate with explicit exact-one-owner semantics.
ALTER TABLE conversations
    ADD COLUMN tutor_user_id BIGINT NULL AFTER student_id;

ALTER TABLE conversations
    MODIFY COLUMN student_id BIGINT NULL;

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_tutor_user
        FOREIGN KEY (tutor_user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE conversations
    ADD CONSTRAINT chk_conversations_exact_one_owner
        CHECK ((student_id IS NOT NULL AND tutor_user_id IS NULL)
            OR (student_id IS NULL AND tutor_user_id IS NOT NULL));

CREATE INDEX idx_conversations_tutor_user_recent
    ON conversations (tutor_user_id, last_message_at, created_at);
