-- Additive AI forecast indexes / status normalize (table created in V33)
-- Do not edit V33.

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

CREATE INDEX IF NOT EXISTS idx_ai_forecast_runs_org_type
    ON ai_forecast_runs (organization_id, forecast_type, created_at DESC);

-- Align recommendation status vocabulary: NEW (canonical), ACKNOWLEDGED, DISMISSED
UPDATE ai_recommendations SET status = 'NEW' WHERE status = 'OPEN';
