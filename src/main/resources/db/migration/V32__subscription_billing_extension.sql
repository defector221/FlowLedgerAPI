-- Subscription billing extension: plan columns, org subscriptions, payments, invoices, webhooks

ALTER TABLE subscription_plans
    ADD COLUMN IF NOT EXISTS price_yearly NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS highlight_plan BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recommended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trial_days INT NOT NULL DEFAULT 0;

UPDATE subscription_plans
SET price_yearly = 0,
    display_order = 1,
    currency = COALESCE(currency, 'INR')
WHERE code = 'FREE';

UPDATE subscription_plans
SET price_yearly = 4990,
    display_order = 2,
    recommended = TRUE,
    currency = COALESCE(currency, 'INR')
WHERE code = 'STARTER';

UPDATE subscription_plans
SET name = 'Pro',
    price_yearly = 19990,
    display_order = 3,
    highlight_plan = TRUE,
    currency = COALESCE(currency, 'INR')
WHERE code = 'BUSINESS';

CREATE TABLE IF NOT EXISTS organization_subscriptions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    plan_id              UUID NOT NULL REFERENCES subscription_plans(id),
    billing_cycle        VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    status               VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    start_date           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date             TIMESTAMPTZ,
    next_billing_date    TIMESTAMPTZ,
    auto_renew           BOOLEAN NOT NULL DEFAULT TRUE,
    payment_provider     VARCHAR(50),
    payment_reference    VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organization_subscriptions_org UNIQUE (organization_id),
    CONSTRAINT chk_org_subscription_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'TRIAL', 'PAST_DUE')),
    CONSTRAINT chk_org_subscription_billing_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY'))
);

CREATE INDEX IF NOT EXISTS idx_organization_subscriptions_plan ON organization_subscriptions(plan_id);
CREATE INDEX IF NOT EXISTS idx_organization_subscriptions_status ON organization_subscriptions(status);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    provider             VARCHAR(50) NOT NULL DEFAULT 'razorpay',
    provider_order_id    VARCHAR(255),
    payment_id           VARCHAR(255),
    amount               NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency             VARCHAR(3) NOT NULL DEFAULT 'INR',
    status               VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    purpose              VARCHAR(30) NOT NULL DEFAULT 'CHECKOUT',
    plan_id              UUID NOT NULL REFERENCES subscription_plans(id),
    billing_cycle        VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    raw_response         JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_txn_purpose CHECK (purpose IN ('CHECKOUT', 'UPGRADE')),
    CONSTRAINT chk_payment_txn_billing_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT chk_payment_txn_status CHECK (status IN ('CREATED', 'PENDING', 'PAID', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_org ON payment_transactions(organization_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_provider_order ON payment_transactions(provider_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON payment_transactions(status);

CREATE TABLE IF NOT EXISTS subscription_invoices (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_number           VARCHAR(100) NOT NULL,
    amount                   NUMERIC(19,4) NOT NULL DEFAULT 0,
    gst                      NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount                 NUMERIC(19,4) NOT NULL DEFAULT 0,
    total                    NUMERIC(19,4) NOT NULL DEFAULT 0,
    paid_at                  TIMESTAMPTZ,
    pdf_url                  VARCHAR(1000),
    payment_transaction_id   UUID REFERENCES payment_transactions(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_invoices_org_number UNIQUE (organization_id, invoice_number)
);

CREATE INDEX IF NOT EXISTS idx_subscription_invoices_org ON subscription_invoices(organization_id);
CREATE INDEX IF NOT EXISTS idx_subscription_invoices_txn ON subscription_invoices(payment_transaction_id);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider          VARCHAR(50) NOT NULL,
    event_id          VARCHAR(255) NOT NULL,
    event_type        VARCHAR(100),
    payload           JSONB NOT NULL DEFAULT '{}'::jsonb,
    signature_valid   BOOLEAN NOT NULL DEFAULT FALSE,
    processed         BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_webhook_events_event UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_provider ON payment_webhook_events(provider);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_processed ON payment_webhook_events(processed);

-- Backfill one org subscription per organization from admin user_subscriptions, else FREE
INSERT INTO organization_subscriptions (organization_id, plan_id, billing_cycle, status, start_date, auto_renew)
SELECT
    o.id,
    COALESCE(
        (
            SELECT us.plan_id
            FROM organization_memberships om
            JOIN organization_membership_roles omr ON omr.membership_id = om.id
            JOIN roles r ON r.id = omr.role_id AND r.code = 'ORGANIZATION_ADMIN'
            JOIN user_subscriptions us ON us.user_id = om.user_id
            WHERE om.organization_id = o.id
              AND om.status = 'ACTIVE'
            ORDER BY om.created_at ASC
            LIMIT 1
        ),
        (SELECT id FROM subscription_plans WHERE code = 'FREE' LIMIT 1)
    ),
    'MONTHLY',
    'ACTIVE',
    NOW(),
    TRUE
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_subscriptions os WHERE os.organization_id = o.id
)
AND EXISTS (SELECT 1 FROM subscription_plans WHERE code = 'FREE');
