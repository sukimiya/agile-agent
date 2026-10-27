-- M2b Q4 redesign (2026-10-27): every delete is soft. Rows are kept and queryable; is_valid = 0 hides them from
-- every normal read, recall, prompt and user API. Derived index rows (items, chunks, turn records) of deleted
-- conversations are physically purged later by the retention job, tiered by their recall reference count.
-- Shared by H2 (MODE=MySQL) and MySQL 8.4; no legacy migration beyond marking existing tombstones.
ALTER TABLE sessions ADD is_valid TINYINT NOT NULL DEFAULT 1;
UPDATE sessions SET is_valid = 0 WHERE deleted_at IS NOT NULL;
ALTER TABLE chat_turns ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE chat_messages ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE chat_tool_events ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_chunks ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_summaries ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_items ADD is_valid TINYINT NOT NULL DEFAULT 1;
-- When the item's last valid source (or its own conversation) was deleted; retention counts from here.
ALTER TABLE memory_items ADD source_deleted_at DATETIME(6) NULL;
-- Manual reference-count decrements (POST /api/memory/items/{id}/recall-count/decrement); the effective count is
-- max(0, recall references - recall_ref_offset).
ALTER TABLE memory_items ADD recall_ref_offset BIGINT NOT NULL DEFAULT 0;
ALTER TABLE memory_item_sources ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_entities ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_relations ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE user_memory_profiles ADD is_valid TINYINT NOT NULL DEFAULT 1;
ALTER TABLE memory_recall_events ADD is_valid TINYINT NOT NULL DEFAULT 1;

CREATE INDEX idx_memory_items_purge ON memory_items (source_deleted_at, status);
CREATE INDEX idx_memory_recall_events_ref ON memory_recall_events (tenant_id, subject, kind, ref_id);

-- Who changed what in the user's memory by hand (no content): actor, action, target, amounts, database time.
CREATE TABLE memory_audit_log (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    actor VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    action VARCHAR(64) NOT NULL,
    target_kind VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    amount BIGINT NULL,
    count_before BIGINT NULL,
    count_after BIGINT NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_memory_audit_log_scope ON memory_audit_log (tenant_id, subject, created_at);
