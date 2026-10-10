-- Internal workflow ownership fields; never supplied by callers or exposed as knowledge metadata.
ALTER TABLE knowledge_documents ADD approval_claim_token VARCHAR(36);
ALTER TABLE knowledge_documents ADD approval_claimed_at TIMESTAMP;

CREATE UNIQUE INDEX idx_knowledge_documents_approval_claim_token
    ON knowledge_documents (approval_claim_token);
