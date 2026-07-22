-- Multi-step AI workflow approval progress on shared approval_requests

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS workflow_draft_id UUID,
    ADD COLUMN IF NOT EXISTS workflow_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_step INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS total_steps INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS steps_snapshot_json TEXT;

ALTER TABLE approval_actions
    ALTER COLUMN action TYPE VARCHAR(64);
