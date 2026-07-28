-- Epic 6: Enterprise Connector sync platform

CREATE TABLE commerce_connector_sync_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    connector_type      VARCHAR(32) NOT NULL,
    sync_type           VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    payload             JSONB NOT NULL DEFAULT '{}',
    attempt_count       INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 5,
    last_error          TEXT,
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connector_sync_jobs_status ON commerce_connector_sync_jobs(status, scheduled_at);

CREATE TABLE commerce_connector_sync_dlq (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID NOT NULL REFERENCES commerce_connector_sync_jobs(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    payload             JSONB NOT NULL,
    error_message       TEXT,
    failed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
