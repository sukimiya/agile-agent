ALTER TABLE hospital_capability_packages
    ADD COLUMN approval_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN reviewed_by VARCHAR(64),
    ADD COLUMN reviewed_at TIMESTAMP(6),
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN last_gate_report TEXT;

ALTER TABLE medical_human_tasks
    ADD COLUMN submission_key VARCHAR(64);

CREATE UNIQUE INDEX ux_medical_human_tasks_submission_key
    ON medical_human_tasks (submission_key)
    WHERE submission_key IS NOT NULL;
