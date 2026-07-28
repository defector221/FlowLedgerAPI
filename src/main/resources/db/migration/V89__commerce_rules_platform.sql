-- Epic 5A: Commerce Rules Platform

CREATE TABLE commerce_promotion_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID REFERENCES retail_stores(id),
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    rule_type           VARCHAR(32) NOT NULL,
    reward_type         VARCHAR(32) NOT NULL,
    discount_percent    NUMERIC(8, 4),
    discount_amount     NUMERIC(18, 2),
    buy_qty             NUMERIC(18, 4),
    get_qty             NUMERIC(18, 4),
    coupon_code         VARCHAR(64),
    min_order_total     NUMERIC(18, 2),
    max_redemptions     INT,
    redemption_count    INT NOT NULL DEFAULT 0,
    priority            INT NOT NULL DEFAULT 100,
    sales_channel       VARCHAR(32),
    starts_at           TIMESTAMPTZ,
    ends_at             TIMESTAMPTZ,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_promotion_rule_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_promotion_rules_org_active ON commerce_promotion_rules(organization_id, active);

CREATE TABLE commerce_promotion_conditions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id             UUID NOT NULL REFERENCES commerce_promotion_rules(id) ON DELETE CASCADE,
    condition_type      VARCHAR(32) NOT NULL,
    operator            VARCHAR(16) NOT NULL DEFAULT 'EQ',
    value_json          JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_promotion_conditions_rule ON commerce_promotion_conditions(rule_id);

CREATE TABLE commerce_promotion_redemptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id             UUID NOT NULL REFERENCES commerce_promotion_rules(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    customer_id         UUID REFERENCES commerce_customers(id),
    order_id            UUID REFERENCES commerce_orders(id),
    coupon_code         VARCHAR(64),
    discount_applied    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    redeemed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promotion_redemptions_rule ON commerce_promotion_redemptions(rule_id);

CREATE TABLE commerce_reward_outcomes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    customer_id         UUID REFERENCES commerce_customers(id),
    order_id            UUID REFERENCES commerce_orders(id),
    rule_id             UUID REFERENCES commerce_promotion_rules(id),
    outcome_type        VARCHAR(32) NOT NULL,
    amount              NUMERIC(18, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    reference_type      VARCHAR(64),
    reference_id        UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reward_outcomes_customer ON commerce_reward_outcomes(customer_id, created_at);
