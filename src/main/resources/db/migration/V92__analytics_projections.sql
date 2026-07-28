-- Epic 8: Analytics event projections

CREATE TABLE analytics_order_facts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL,
    store_id            UUID,
    order_id            UUID NOT NULL UNIQUE,
    customer_id         UUID,
    channel             VARCHAR(32),
    order_total         NUMERIC(18, 2) NOT NULL,
    discount_total      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL,
    placed_at           TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_order_facts_org ON analytics_order_facts(organization_id, placed_at);

CREATE TABLE analytics_promotion_facts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL,
    rule_id             UUID,
    order_id            UUID,
    discount_applied    NUMERIC(18, 2) NOT NULL,
    outcome_type        VARCHAR(32),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE analytics_funnel_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID,
    customer_id         UUID,
    event_type          VARCHAR(64) NOT NULL,
    session_id          UUID,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_funnel_org ON analytics_funnel_events(organization_id, event_type, occurred_at);
