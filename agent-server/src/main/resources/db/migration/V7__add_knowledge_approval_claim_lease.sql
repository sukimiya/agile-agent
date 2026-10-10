-- Internal approval ownership. approval_version is a monotonic fencing generation per document;
-- approval_owner records the authenticated reviewer, never the document uploader/creator.
ALTER TABLE knowledge_documents ADD approval_owner VARCHAR(255);
ALTER TABLE knowledge_documents ADD approval_claimed_at TIMESTAMP;
ALTER TABLE knowledge_documents ADD approval_version BIGINT NOT NULL DEFAULT 0;

-- Preserve already-approved legacy content as immutable generation zero while binding it
-- to the reviewer identity recorded by the original workflow.
UPDATE knowledge_documents
SET approval_owner = reviewed_by_user_id
WHERE status = 'APPROVED' AND reviewed_by_user_id IS NOT NULL;

CREATE INDEX idx_knowledge_documents_approval_lease
    ON knowledge_documents (status, approval_claimed_at);
