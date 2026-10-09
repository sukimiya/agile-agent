CREATE TABLE knowledge_documents (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    temp_path VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary TEXT,
    uploader_user_id VARCHAR(64) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    reviewed_by_user_id VARCHAR(64),
    reviewed_at DATETIME(6)
);

CREATE INDEX idx_knowledge_documents_status ON knowledge_documents (status, uploaded_at);
