-- Plan 4 (C04): rule engine + E-call + human takeover + fixed-script state machine.
-- New tables medical_rules / medical_task_events; medical_human_task extension columns
-- for E-call tasks; partial unique index capping open E-calls at one per conversation.
-- Ruling R1: scope_key is a generated column so the same rule_code can exist once per
-- scope (GLOBAL/TENANT/HOSPITAL) — the engine merges same-code rules strictest-wins.

CREATE TABLE medical_rules (
    id VARCHAR(64) PRIMARY KEY,
    rule_code VARCHAR(128) NOT NULL,
    rule_kind VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    tenant_id VARCHAR(64),
    tenant_name VARCHAR(255),
    org_id VARCHAR(64),
    org_name VARCHAR(255),
    hospital_code VARCHAR(64),
    scope_key VARCHAR(255) GENERATED ALWAYS AS (
        scope_type || '|' || COALESCE(tenant_id, '-') || '|' || COALESCE(hospital_code, '-')) STORED,
    content_json TEXT NOT NULL,
    source VARCHAR(16) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,
    approval_version BIGINT NOT NULL DEFAULT 0,
    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMP,
    rejection_reason TEXT,
    last_validation_report TEXT,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_medical_rules_code_scope UNIQUE (rule_code, scope_key)
);

CREATE INDEX idx_medical_rules_status ON medical_rules (status, valid_until);
CREATE INDEX idx_medical_rules_scope ON medical_rules (scope_type, tenant_id, status);

ALTER TABLE medical_human_tasks
    ADD COLUMN task_kind VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN priority INT,
    ADD COLUMN sla_deadline TIMESTAMP(6),
    ADD COLUMN escalation_level INT NOT NULL DEFAULT 0,
    ADD COLUMN claimed_by VARCHAR(64),
    ADD COLUMN claimed_at TIMESTAMP(6),
    ADD COLUMN completed_at TIMESTAMP(6),
    ADD COLUMN resolution_note TEXT;

-- At most one open (not COMPLETED) E-call per conversation (spec §4 防双 E-call).
CREATE UNIQUE INDEX ux_medical_human_tasks_open_ecall
    ON medical_human_tasks (conversation_id)
    WHERE task_kind = 'ECALL' AND status <> 'COMPLETED';

CREATE TABLE medical_task_events (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    actor VARCHAR(64),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_medical_task_events_task ON medical_task_events (task_id, created_at);

-- Flagship emergency rule seed (spec §4/§6): an empty database already carries the
-- GLOBAL safety baseline. Scripts verbatim from the design spec. Compact JSON so the
-- migration test can assert on exact substrings like "slaSeconds":0.
-- Single-quoted literal, not dollar-quoting: Flyway placeholder replacement is enabled
-- and a dollar tag followed by an opening brace reads as an unresolvable placeholder.
INSERT INTO medical_rules
    (id, rule_code, rule_kind, scope_type, content_json, source,
     valid_from, valid_until, status, approval_version, reviewed_by, reviewed_at, created_by)
VALUES
    ('rule-global-emergency-baseline', 'global-emergency-baseline', 'EMERGENCY', 'GLOBAL',
     '{"schemaVersion":1,"kind":"EMERGENCY","condition":{"triageCategories":["EMERGENCY","CRITICAL"],"diseaseNames":["急性心肌梗死","心肌梗死","脑卒中","急性脑卒中","心源性猝死","主动脉夹层","肺栓塞","严重创伤","休克","昏迷","大出血","癫痫持续状态","stroke","acute myocardial infarction","myocardial infarction"]},"action":{"priority":100,"slaSeconds":0,"escalationLevels":[300,900,1800],"awaitingTakeoverScript":"我们已立即为您联系专事专办医疗协调专员，请稍候。在专员接入前，本平台不提供任何医疗建议。如情况危急，请立即拨打 120 或前往就近急诊。","takenOverScript":"专事专办医疗协调专员已接管本次会话，我将仅提供辅助性行政信息。"}}',
     'migration', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2099-12-31 23:59:59',
     'APPROVED', 0, 'migration', CURRENT_TIMESTAMP, 'migration');
