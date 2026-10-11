CREATE TABLE llm_provider_preset (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model VARCHAR(255) NOT NULL,
    balance_path VARCHAR(500),
    created_by_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_llm_provider_preset_name ON llm_provider_preset (name);
