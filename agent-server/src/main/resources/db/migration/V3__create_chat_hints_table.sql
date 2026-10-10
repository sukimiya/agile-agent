CREATE TABLE chat_hints (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    content TEXT NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_chat_hints_created_at_id ON chat_hints (created_at, id);
