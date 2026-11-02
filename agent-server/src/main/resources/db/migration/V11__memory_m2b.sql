-- Durable memory M2b (Task 1): long-term memory items, sources, user profile, one-hop entities/relations,
-- per-turn recall events, daily token usage and per-tenant quota overrides (D11=A). Shared by H2 (MODE=MySQL)
-- and MySQL 8.4. Conventions follow V8/V10: identity columns utf8mb4_bin, bodies MEDIUMTEXT, no foreign keys,
-- every table repeats tenant_id/subject so reads and deletes can be scope-checked directly.

-- Long-term memory items judged from turns (M2b Task 3 writes; Task 1 only the schema).
-- conversation_id NULL = user-level (cross-conversation); non-null = session-scoped.
-- status: CANDIDATE|ACTIVE|CONFLICT|SUPERSEDED|INACTIVE|DELETED
-- assertion: USER_CONFIRMED|USER_STATED|INFERRED
CREATE TABLE memory_items (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    item_type VARCHAR(64) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    assertion VARCHAR(32) NOT NULL,
    importance DOUBLE NULL,
    confidence DOUBLE NULL,
    valid_until DATETIME(6) NULL,
    last_recalled_at DATETIME(6) NULL,
    superseded_by VARCHAR(64) NULL COLLATE utf8mb4_bin,
    rules_version INT NOT NULL,
    judge_model VARCHAR(255) NOT NULL,
    reason MEDIUMTEXT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_memory_items_scope_status ON memory_items (tenant_id, subject, status, updated_at);
CREATE INDEX idx_memory_items_conversation ON memory_items (conversation_id, status);

-- Provenance of an item: which message(s) it was judged from. Unique (item_id, message_id).
CREATE TABLE memory_item_sources (
    item_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    message_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    source_type VARCHAR(32) NOT NULL,
    evidence_excerpt MEDIUMTEXT NOT NULL,
    PRIMARY KEY (item_id, message_id)
);
CREATE INDEX idx_memory_item_sources_message ON memory_item_sources (message_id);
CREATE INDEX idx_memory_item_sources_conversation ON memory_item_sources (conversation_id);

-- Compact user memory profile snapshot (M2b Task 4).
CREATE TABLE user_memory_profiles (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    content MEDIUMTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    built_through BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, subject)
);

-- Named entities in the user's memory graph (one-hop relations in Task 4).
CREATE TABLE memory_entities (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    name VARCHAR(512) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    normalized_key VARCHAR(191) NOT NULL COLLATE utf8mb4_bin,
    CONSTRAINT uq_memory_entities_scope_key UNIQUE (tenant_id, subject, normalized_key)
);

-- One-hop relations between entities; status ACTIVE|SUPERSEDED|INACTIVE.
CREATE TABLE memory_relations (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    from_entity VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    relation VARCHAR(64) NOT NULL,
    to_entity VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    item_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    valid_from DATETIME(6) NULL,
    valid_until DATETIME(6) NULL,
    status VARCHAR(32) NOT NULL
);
CREATE INDEX idx_memory_relations_scope ON memory_relations (tenant_id, subject, status);
CREATE INDEX idx_memory_relations_from ON memory_relations (from_entity, status);
CREATE INDEX idx_memory_relations_to ON memory_relations (to_entity, status);

-- What was actually injected into a turn's prompt (M2b Task 5).
-- kind: CHUNK|SUMMARY|ITEM|PROFILE|RELATION
CREATE TABLE memory_recall_events (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    turn_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    kind VARCHAR(32) NOT NULL,
    ref_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    reason VARCHAR(64) NULL,
    score DOUBLE NULL,
    `rank` INT NULL,
    source_types VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_memory_recall_events_turn ON memory_recall_events (turn_id, `rank`);
CREATE INDEX idx_memory_recall_events_scope ON memory_recall_events (tenant_id, subject, created_at);

-- Per-tenant daily token usage for consolidation (M2b Task 2).
CREATE TABLE memory_token_usage (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    usage_date DATE NOT NULL,
    tokens BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, usage_date)
);

-- Per-tenant daily consolidation token limit override (D11=A); absent row means the global default.
CREATE TABLE memory_tenant_quotas (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    daily_token_limit BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
