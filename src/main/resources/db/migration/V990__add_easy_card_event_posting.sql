-- EASY Phase 5A.1: admin-reviewed card events can post to the existing EASY ledger.
-- Additive only. A card event links to at most one easy_transactions row (idempotency guard).
ALTER TABLE easy_card_events
    ADD COLUMN posted_txn_id BIGINT NULL AFTER reviewed_by,
    ADD CONSTRAINT fk_easy_card_event_posted_txn FOREIGN KEY (posted_txn_id) REFERENCES easy_transactions (txn_id),
    ADD CONSTRAINT uq_easy_card_event_posted_txn UNIQUE (posted_txn_id),
    ADD INDEX idx_easy_card_event_business_review (business_id, review_status, occurred_at);
