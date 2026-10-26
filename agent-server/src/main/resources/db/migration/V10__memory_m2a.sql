-- Durable memory M2a: typed jobs, turn memory records, recall chunks, index progress, the cross-conversation
-- switch (D1) and per-preset model limits (D2). Shared by H2 (MODE=MySQL) and MySQL 8.4; vendor-specific DDL
-- (the ngram FULLTEXT indexes, D7/D8) lives in db/vendor/{vendor}/V10_1__memory_m2a_fulltext.sql. Conventions
-- follow V8: identity columns use utf8mb4_bin, bodies are MEDIUMTEXT, no foreign keys, every table repeats
-- tenant_id/subject so reads and deletes can be scope-checked directly.

-- Jobs become typed and deduplicated: dedupe_key is unique per job_type (NULLs never collide), payload carries
-- the job's JSON arguments. The M1 deletion job type is renamed to CONVERSATION_CLEANUP; pending ones keep running.
ALTER TABLE memory_jobs ADD dedupe_key VARCHAR(191) NULL COLLATE utf8mb4_bin;
ALTER TABLE memory_jobs ADD payload MEDIUMTEXT NULL;
ALTER TABLE memory_jobs ADD CONSTRAINT uq_memory_jobs_dedupe UNIQUE (job_type, dedupe_key);
CREATE INDEX idx_memory_jobs_conversation ON memory_jobs (conversation_id, job_type);
UPDATE memory_jobs SET job_type = 'CONVERSATION_CLEANUP' WHERE job_type = 'DELETE_CONVERSATION';

-- Turn memory records (M2a Task 4, D18/D21): one model-written memory-record-v1 record per completed turn, the
-- search index of long-term memory. record_json is the validated record (with its server-filled source);
-- search_text joins its summary, entities, key facts and keywords for full-text search (ngram FULLTEXT on MySQL,
-- LIKE on H2). Records never replace the originals in prompts; a hit leads back to the turn's messages. A newer
-- schema version of the same turn sets superseded_at on the old one; one row per turn and schema version.
CREATE TABLE memory_summaries (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    turn_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    from_sequence BIGINT NOT NULL,
    to_sequence BIGINT NOT NULL,
    from_message_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    to_message_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    record_json MEDIUMTEXT NOT NULL,
    search_text MEDIUMTEXT NOT NULL,
    memory_type VARCHAR(16) NOT NULL,
    has_decision BOOLEAN NOT NULL DEFAULT FALSE,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MODEL_SUMMARY',
    generator_model VARCHAR(255) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    superseded_at DATETIME(6) NULL,
    CONSTRAINT uq_memory_summaries_turn UNIQUE (turn_id, schema_version)
);
CREATE INDEX idx_memory_summaries_scope ON memory_summaries (tenant_id, subject, conversation_id, to_sequence);

-- Recall chunks: one per completed turn, split by paragraph above the chunk size (D6). prev/next link the
-- neighbours of one conversation; source_types lists the provenance of the covered messages.
CREATE TABLE memory_chunks (
    id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    turn_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,
    from_sequence BIGINT NOT NULL,
    to_sequence BIGINT NOT NULL,
    prev_chunk_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    next_chunk_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    content MEDIUMTEXT NOT NULL,
    source_types VARCHAR(255) NOT NULL,
    entities_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_memory_chunks_scope ON memory_chunks (tenant_id, subject, conversation_id, from_sequence);
CREATE INDEX idx_memory_chunks_turn ON memory_chunks (turn_id);

-- How far indexing and turn records have got per conversation; a failed job leaves it untouched.
CREATE TABLE memory_index_progress (
    conversation_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    indexed_through_sequence BIGINT NOT NULL DEFAULT 0,
    summarized_through_sequence BIGINT NOT NULL DEFAULT 0,
    -- Consecutive invalid record outputs for the turn after summarized_through_sequence (D23); reset on progress.
    summary_invalid_turn_id VARCHAR(64) NULL COLLATE utf8mb4_bin,
    summary_invalid_attempts INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL
);

-- Per-user memory switches (D1): cross-conversation recall is off until the user turns it on.
CREATE TABLE user_memory_settings (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    subject VARCHAR(255) NOT NULL COLLATE utf8mb4_bin,
    cross_conversation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, subject)
);

-- Model limits per provider preset (D2); NULL means the global defaults (32,768 window, 4,096 output reserve).
ALTER TABLE llm_provider_preset ADD context_window_tokens INT NULL;
ALTER TABLE llm_provider_preset ADD max_output_tokens INT NULL;

-- The idle scan (D19) looks up turns per conversation by status and time.
CREATE INDEX idx_chat_turns_conversation_status ON chat_turns (conversation_id, status);
