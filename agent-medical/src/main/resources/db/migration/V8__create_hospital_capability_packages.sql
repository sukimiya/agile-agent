CREATE TABLE hospital_capability_packages (
    id VARCHAR(64) PRIMARY KEY,
    hospital_code VARCHAR(64) NOT NULL UNIQUE,
    hospital_name VARCHAR(255) NOT NULL,
    routing_description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    schema_version INTEGER NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(500) NOT NULL,
    valid_from TIMESTAMP(6) NOT NULL,
    valid_until TIMESTAMP(6) NOT NULL,
    scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
    tenant_id VARCHAR(128),
    tenant_name VARCHAR(255),
    org_id VARCHAR(128),
    org_name VARCHAR(255),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hospital_packages_scope ON hospital_capability_packages (tenant_id, org_id, status);
CREATE INDEX idx_hospital_packages_status ON hospital_capability_packages (status, valid_until);
