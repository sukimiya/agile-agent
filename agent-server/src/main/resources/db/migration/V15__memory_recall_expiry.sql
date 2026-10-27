-- Recall records expire (M2b, user decision 2026-10-27): after app.memory.purge.recall-record-ttl (default 30 days)
-- the periodic purge job marks them expired. They are never deleted; expired records no longer count as recall
-- references, so retention tiers can fall.
ALTER TABLE memory_recall_events ADD COLUMN expired_at DATETIME(6) NULL;
CREATE INDEX idx_memory_recall_events_expiry ON memory_recall_events (expired_at, created_at);
