CREATE TABLE medical_human_tasks (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    hospital_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_medical_human_tasks_status ON medical_human_tasks (status, created_at);
CREATE INDEX idx_medical_human_tasks_conversation ON medical_human_tasks (conversation_id);
