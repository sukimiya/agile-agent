ALTER TABLE knowledge_documents
    ADD scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE knowledge_documents ADD tenant_id VARCHAR(128);
ALTER TABLE knowledge_documents ADD tenant_name VARCHAR(255);
ALTER TABLE knowledge_documents ADD org_id VARCHAR(128);
ALTER TABLE knowledge_documents ADD org_name VARCHAR(255);
-- Internal workflow ownership token; never supplied by callers or exposed as knowledge metadata.
ALTER TABLE knowledge_documents ADD approval_claim_token VARCHAR(36);

UPDATE knowledge_documents
SET scope_type = 'GLOBAL',
    tenant_id = NULL,
    tenant_name = NULL,
    org_id = NULL,
    org_name = NULL;

CREATE INDEX idx_knowledge_documents_scope
    ON knowledge_documents (tenant_id, org_id, status);

CREATE UNIQUE INDEX idx_knowledge_documents_approval_claim_token
    ON knowledge_documents (approval_claim_token);
