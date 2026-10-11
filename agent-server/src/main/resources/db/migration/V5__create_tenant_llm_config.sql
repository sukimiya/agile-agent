CREATE TABLE tenant_llm_config (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    preset_id VARCHAR(64) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_tenant_llm_config_tenant_id UNIQUE (tenant_id)
);

CREATE INDEX idx_tenant_llm_config_preset_id ON tenant_llm_config (preset_id);
