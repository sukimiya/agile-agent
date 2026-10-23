-- Idempotency-Key scoped to the user (spec 13, duplicate-billing release gate). A key is unique per
-- (tenant_id, subject, idempotency_key), independent of the conversation, so a retried first request without a
-- conversationId finds its turn instead of creating a new conversation. idempotency_key is NULL for requests
-- without a key and after the retention period (default 24 h), which releases the key without deleting the turn
-- (several NULLs never collide). request_hash now holds the request fingerprint: SHA-256 (hex) over endpoint,
-- client-supplied conversationId, message content and attachment ids. The per-conversation request id is
-- dropped; no data migration is needed (final design).
ALTER TABLE chat_turns DROP CONSTRAINT uq_chat_turns_request;
ALTER TABLE chat_turns DROP COLUMN request_id;
ALTER TABLE chat_turns ADD idempotency_key VARCHAR(128) NULL COLLATE utf8mb4_bin;
ALTER TABLE chat_turns ADD CONSTRAINT uq_chat_turns_idempotency UNIQUE (tenant_id, subject, idempotency_key);
CREATE INDEX idx_chat_turns_created ON chat_turns (created_at);
