-- FlowLedger AI Platform foundation (Phases 1–4 schema + Phase 7 forecast stub)

INSERT INTO permissions (id, code, name, module, description) VALUES
    (gen_random_uuid(), 'AI_CHAT', 'Use AI chat', 'AI', 'Chat with FlowLedger AI agents'),
    (gen_random_uuid(), 'AI_ANALYSIS', 'Run AI analysis', 'AI', 'Request AI analysis of ERP data'),
    (gen_random_uuid(), 'AI_RECOMMENDATION', 'Manage AI recommendations', 'AI', 'View and act on AI recommendations'),
    (gen_random_uuid(), 'AI_ADMIN', 'Administer AI platform', 'AI', 'Manage AI knowledge and settings')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code IN ('AI_CHAT', 'AI_ANALYSIS', 'AI_RECOMMENDATION', 'AI_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS ai_conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(255),
    agent_type      VARCHAR(50),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_conversations_org_user
    ON ai_conversations (organization_id, user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_messages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id    UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    role               VARCHAR(32) NOT NULL,
    content            TEXT NOT NULL,
    model              VARCHAR(100),
    prompt_tokens      INT,
    completion_tokens  INT,
    latency_ms         INT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_messages_conversation
    ON ai_messages (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS ai_audit_log (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    user_id            UUID,
    action             VARCHAR(100) NOT NULL,
    request_summary    TEXT,
    response_summary   TEXT,
    model              VARCHAR(100),
    tokens             INT,
    latency_ms         INT,
    error              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_audit_org_created
    ON ai_audit_log (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    type                 VARCHAR(64) NOT NULL,
    priority             VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    confidence           NUMERIC(5, 4),
    reason               TEXT,
    evidence             JSONB,
    suggested_action     TEXT,
    status               VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    related_entity_type  VARCHAR(64),
    related_entity_id    UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_recommendations_org_status
    ON ai_recommendations (organization_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_knowledge_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    title           VARCHAR(255) NOT NULL,
    doc_type        VARCHAR(64) NOT NULL,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_org_type
    ON ai_knowledge_documents (organization_id, doc_type);

CREATE TABLE IF NOT EXISTS ai_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    source_type     VARCHAR(64) NOT NULL,
    source_id       UUID NOT NULL,
    content_hash    VARCHAR(64),
    embedding_json  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_embeddings_source
    ON ai_embeddings (organization_id, source_type, source_id);

-- Optional pgvector; ignored when extension is unavailable
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension not available; using embedding_json text storage';
END $$;

-- Phase 7 stub
CREATE TABLE IF NOT EXISTS ai_forecast_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    forecast_type   VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    params_json     JSONB,
    result_json     JSONB,
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
