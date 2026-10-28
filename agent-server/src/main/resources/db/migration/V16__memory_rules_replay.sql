-- Rules versioned replay (M2b Task 7, D13=A, user decisions 2026-10-28). A replay is an explicit, resumable job
-- that a platform admin starts for one tenant; it re-judges old-Rules INFERRED items one by one.

-- A replacement item inherits the effective recall count of the item it supersedes; recall records are not
-- copied. The effective count is max(0, live references + inherited_recall_count - recall_ref_offset).
ALTER TABLE memory_items ADD COLUMN inherited_recall_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE memory_replays (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    from_rules_version INT NULL,
    to_rules_version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    superseded_count BIGINT NOT NULL DEFAULT 0,
    inactivated_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL
);
CREATE INDEX idx_memory_replays_tenant ON memory_replays (tenant_id, created_at);

-- Audit mapping, one row per old item handled by a replay: SUPERSEDED (new_item_id set), INACTIVATED (no new item)
-- or SKIPPED (no live source conversation, or the item changed meanwhile).
CREATE TABLE memory_replay_items (
    replay_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    item_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    new_item_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    outcome VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (replay_id, item_id)
);
CREATE INDEX idx_memory_replay_items_conversation ON memory_replay_items (tenant_id, subject, conversation_id);
