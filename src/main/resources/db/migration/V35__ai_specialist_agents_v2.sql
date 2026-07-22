-- AI Specialist Agents v2: workflow drafts, agent-run audit, AI_WORKFLOW permission

INSERT INTO permissions (id, code, name, module, description) VALUES
    (gen_random_uuid(), 'AI_WORKFLOW', 'Manage AI workflows', 'AI', 'Create and activate advisory AI workflow drafts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code = 'AI_WORKFLOW'
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS ai_agent_runs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    conversation_id    UUID REFERENCES ai_conversations(id) ON DELETE SET NULL,
    user_id            UUID REFERENCES users(id),
    primary_agent      VARCHAR(64) NOT NULL,
    consulted_agents   TEXT,
    message_preview    VARCHAR(500),
    latency_ms         INT,
    status             VARCHAR(32) NOT NULL DEFAULT 'OK',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_org_created
    ON ai_agent_runs (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_workflow_drafts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    created_by         UUID REFERENCES users(id),
    name               VARCHAR(255) NOT NULL,
    trigger_type       VARCHAR(64) NOT NULL DEFAULT 'MANUAL',
    description        TEXT,
    conditions_json    TEXT,
    steps_json         TEXT,
    suggested_approvers TEXT,
    status             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_drafts_org
    ON ai_workflow_drafts (organization_id, updated_at DESC);
