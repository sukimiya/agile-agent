-- Durable memory (M1). Shared by H2 (MODE=MySQL, tests) and MySQL 8.4 (production).
-- Identity columns (ids, tenant_id, subject/user_id) use utf8mb4_bin so identities that differ only by case
-- never match; H2 compares case-sensitively by default. Message bodies and tool records are JSON text in
-- MEDIUMTEXT (MySQL TEXT stops at 64 KB); the application enforces app.memory.max-message-bytes and rejects
-- larger values instead of truncating. "NOT NULL COLLATE ..." ordering is required by H2 for MODIFY COLUMN.
-- No foreign keys, matching the existing schema; every table repeats tenant_id/subject so each read and
-- update can be scope-checked directly.

-- Sessions: add tenant ownership, a revision for cache validation and a tombstone. Existing rows are not
-- back-filled: they keep tenant_id NULL and are never returned by scoped queries (no legacy migration).
ALTER TABLE sessions MODIFY COLUMN id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin;
ALTER TABLE sessions MODIFY COLUMN user_id VARCHAR(255) NOT NULL COLLATE utf8mb4_bin;
ALTER TABLE sessions ADD tenant_id VARCHAR(128) NULL COLLATE utf8mb4_bin;
ALTER TABLE sessions ADD revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sessions ADD deleted_at DATETIME(6) NULL;
CREATE INDEX idx_sessions_scope_updated ON sessions (tenant_id, user_id, updated_at);

-- One row per accepted request. request_id is the Idempotency-Key or a server-generated id; request_hash is
-- the SHA-256 (hex) of the input so a reused key with different text can be detected. active_conversation_id
-- equals conversation_id while ACCEPTED/RUNNING and is NULL once terminal, so its unique constraint admits at
-- most one active turn per conversation. fence increases whenever execution rights move (recovery, deletion).
CREATE TABLE chat_turns (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    request_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    active_conversation_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    fence BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(64) NULL COLLATE utf8mb4_bin,
    lease_expires_at DATETIME(6) NULL,
    usage_observed BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT uq_chat_turns_request UNIQUE (conversation_id, request_id),
    CONSTRAINT uq_chat_turns_active UNIQUE (active_conversation_id)
);
CREATE INDEX idx_chat_turns_lease ON chat_turns (status, lease_expires_at);
CREATE INDEX idx_chat_turns_scope ON chat_turns (tenant_id, subject, conversation_id);

-- Original messages, ordered per conversation. payload is the serialized LlmClient.Message JSON; source_type
-- tags provenance (USER_INPUT, ASSISTANT, TOOL_RESULT, KNOWLEDGE_DOC, WEB, MODEL_SUMMARY). Assistant rows are
-- updated in place by checkpoints. Messages do not expire.
CREATE TABLE chat_messages (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    turn_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    sequence BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_chat_messages_sequence UNIQUE (conversation_id, sequence)
);
CREATE INDEX idx_chat_messages_turn ON chat_messages (turn_id);
CREATE INDEX idx_chat_messages_scope ON chat_messages (tenant_id, subject, conversation_id, sequence);

-- Traceable tool executions. call_id is unique only within its turn: the same model call id in another turn
-- is a different event. arguments/result hold sanitized JSON text only; status UNCERTAIN marks external
-- writes whose outcome is unknown after cancel/interrupt (never claimed as undone).
CREATE TABLE chat_tool_events (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    turn_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    call_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    capability VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    arguments MEDIUMTEXT NULL,
    result MEDIUMTEXT NULL,
    error_type VARCHAR(64) NULL,
    started_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT uq_chat_tool_events_call UNIQUE (turn_id, call_id)
);
CREATE INDEX idx_chat_tool_events_scope ON chat_tool_events (tenant_id, subject, conversation_id);

-- Retryable background work (M1: conversation deletion cleanup) with lease and attempt tracking.
CREATE TABLE memory_jobs (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(64) NULL COLLATE utf8mb4_bin,
    lease_expires_at DATETIME(6) NULL,
    available_at DATETIME(6) NOT NULL,
    last_error VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_memory_jobs_pending ON memory_jobs (status, available_at);

-- Single-instance guard: one heartbeat row per running instance; another live row in the same namespace is a
-- conflict (alert and refuse new turns).
CREATE TABLE service_instances (
    instance_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    namespace VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    host VARCHAR(255) NULL,
    started_at DATETIME(6) NOT NULL,
    heartbeat_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_service_instances_namespace ON service_instances (namespace, heartbeat_at);
