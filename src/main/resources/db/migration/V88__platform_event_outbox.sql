-- Epic 5A: Platform Event Bus (transactional outbox)

CREATE TABLE platform_event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(128) NOT NULL,
    event_version   INT NOT NULL DEFAULT 1,
    organization_id UUID,
    aggregate_type  VARCHAR(64),
    aggregate_id    UUID,
    payload         JSONB NOT NULL DEFAULT '{}',
    correlation_id  UUID,
    actor_id        UUID,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_event_outbox_unpublished ON platform_event_outbox(published_at) WHERE published_at IS NULL;
CREATE INDEX idx_platform_event_outbox_type ON platform_event_outbox(event_type, occurred_at);
CREATE INDEX idx_platform_event_outbox_org ON platform_event_outbox(organization_id, occurred_at);
