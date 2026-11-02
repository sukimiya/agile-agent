-- In-chat forget / restore (M2b T5 follow-up, user decision 2026-10-27).
-- A turn answered by a memory command (FORGET / RESTORE) is marked so memory judgement never derives new items
-- from it; its raw text stays recallable like any other turn.
ALTER TABLE chat_turns ADD COLUMN memory_command VARCHAR(16) NULL;

-- At most one open numbered pick per conversation: the listed candidate ids in order, valid until expires_at
-- (database time). Consumed by the next turn of the conversation, whatever it says.
CREATE TABLE memory_command_pending (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    token VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    command VARCHAR(16) NOT NULL,
    candidate_ids VARCHAR(1024) NOT NULL COLLATE utf8mb4_bin,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, subject, conversation_id)
);
