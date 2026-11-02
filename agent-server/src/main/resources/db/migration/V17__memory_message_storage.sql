-- Tiered compression of stored original messages (M2b Task 9, user decisions 2026-10-30).
-- A text field is plain (<field>_codec NULL), inline zstd ('zstd', data in <field>_blob, <field>_tier WARM/COLD)
-- or shared ('ref', data in message_blobs of the same tenant keyed by <field>_sha256). <field>_sha256 is the
-- SHA-256 (hex) of the plain UTF-8 text and is verified on every read. Rows are soft-deleted only; compressed data
-- and shared blobs are never physically removed. Turn records, chunks and the full-text index stay plain.
ALTER TABLE chat_messages MODIFY payload MEDIUMTEXT NULL;
ALTER TABLE chat_messages ADD COLUMN payload_codec VARCHAR(16) NULL;
ALTER TABLE chat_messages ADD COLUMN payload_tier VARCHAR(8) NULL;
ALTER TABLE chat_messages ADD COLUMN payload_blob MEDIUMBLOB NULL;
ALTER TABLE chat_messages ADD COLUMN payload_sha256 CHAR(64) NULL COLLATE utf8mb4_bin;
CREATE INDEX idx_chat_messages_storage ON chat_messages (payload_codec, payload_tier, created_at);

ALTER TABLE chat_tool_events ADD COLUMN arguments_codec VARCHAR(16) NULL;
ALTER TABLE chat_tool_events ADD COLUMN arguments_tier VARCHAR(8) NULL;
ALTER TABLE chat_tool_events ADD COLUMN arguments_blob MEDIUMBLOB NULL;
ALTER TABLE chat_tool_events ADD COLUMN arguments_sha256 CHAR(64) NULL COLLATE utf8mb4_bin;
ALTER TABLE chat_tool_events ADD COLUMN result_codec VARCHAR(16) NULL;
ALTER TABLE chat_tool_events ADD COLUMN result_tier VARCHAR(8) NULL;
ALTER TABLE chat_tool_events ADD COLUMN result_blob MEDIUMBLOB NULL;
ALTER TABLE chat_tool_events ADD COLUMN result_sha256 CHAR(64) NULL COLLATE utf8mb4_bin;
CREATE INDEX idx_chat_tool_events_storage ON chat_tool_events (started_at);

-- Identical large texts (>= dedupe-min-bytes) stored once per tenant; never shared across tenants.
-- newest_ref_at: creation time of the newest message or tool call referencing it (drives the cold tier).
CREATE TABLE message_blobs (
    tenant_id VARCHAR(128) NOT NULL COLLATE utf8mb4_bin,
    content_hash CHAR(64) NOT NULL COLLATE utf8mb4_bin,
    codec VARCHAR(16) NOT NULL,
    tier VARCHAR(8) NOT NULL,
    data MEDIUMBLOB NOT NULL,
    plain_bytes BIGINT NOT NULL,
    newest_ref_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, content_hash)
);
CREATE INDEX idx_message_blobs_tier ON message_blobs (tier, newest_ref_at);
