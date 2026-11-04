-- Relation write path (T1, user decisions 2026-11-04): every relation belongs to a memory item and is visible exactly
-- while that item is (ACTIVE, valid, in the recall scope). Relations without a live item are dropped (no production
-- path wrote relations before; final design over backward compatibility). One row per edge of an item; owner-led
-- indexes for one-hop lookups by entity. The user-self entity uses the reserved normalized_key '@self' with
-- entity_type 'SELF' (created per user on first use; ordinary names never normalize to it).
DELETE FROM memory_relations WHERE item_id IS NULL
    OR NOT EXISTS (SELECT 1 FROM memory_items i WHERE i.id = memory_relations.item_id);
ALTER TABLE memory_relations MODIFY COLUMN item_id VARCHAR(64) NOT NULL COLLATE utf8mb4_bin;
ALTER TABLE memory_relations MODIFY COLUMN relation VARCHAR(64) NOT NULL COLLATE utf8mb4_bin;
ALTER TABLE memory_relations ADD CONSTRAINT uq_memory_relations_item_edge
    UNIQUE (tenant_id, subject, item_id, from_entity, relation, to_entity);
ALTER TABLE memory_relations DROP INDEX idx_memory_relations_from;
ALTER TABLE memory_relations DROP INDEX idx_memory_relations_to;
CREATE INDEX idx_memory_relations_scope_from ON memory_relations (tenant_id, subject, from_entity);
CREATE INDEX idx_memory_relations_scope_to ON memory_relations (tenant_id, subject, to_entity);
CREATE INDEX idx_memory_relations_item ON memory_relations (item_id);
