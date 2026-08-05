-- E2 AI automation engine

CREATE TABLE IF NOT EXISTS ai_automations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(32) NOT NULL,
    cron_expression VARCHAR(64),
    event_type VARCHAR(64),
    signal_type VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    action_config_json TEXT NOT NULL DEFAULT '{}',
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'PAUSED',
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_automations_org_status ON ai_automations(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_ai_automations_next_run ON ai_automations(status, next_run_at)
    WHERE status = 'ACTIVE' AND trigger_type = 'CRON';

CREATE TABLE IF NOT EXISTS ai_automation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    automation_id UUID NOT NULL REFERENCES ai_automations(id) ON DELETE CASCADE,
    trigger_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    signal_count INT NOT NULL DEFAULT 0,
    action_count INT NOT NULL DEFAULT 0,
    summary TEXT,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ai_automation_runs_automation ON ai_automation_runs(automation_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_automation_runs_org ON ai_automation_runs(organization_id, started_at DESC);

CREATE TABLE IF NOT EXISTS ai_usage_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL UNIQUE,
    daily_token_limit INT NOT NULL DEFAULT 200000,
    tokens_used_today INT NOT NULL DEFAULT 0,
    usage_date DATE NOT NULL DEFAULT CURRENT_DATE,
    hard_stop BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_forecast_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    feature_date DATE NOT NULL,
    product_id UUID,
    warehouse_id UUID,
    sales_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    revenue NUMERIC(18, 4) NOT NULL DEFAULT 0,
    stockout_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ar_outstanding NUMERIC(18, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_forecast_features UNIQUE (organization_id, feature_date, product_id, warehouse_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_forecast_features_org_date ON ai_forecast_features(organization_id, feature_date DESC);

ALTER TABLE ai_forecast_runs ADD COLUMN IF NOT EXISTS mape NUMERIC(10, 4);
ALTER TABLE ai_forecast_runs ADD COLUMN IF NOT EXISTS method VARCHAR(64);
