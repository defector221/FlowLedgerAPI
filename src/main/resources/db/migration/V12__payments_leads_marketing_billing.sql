-- Payments cancel support, payment reminders, CRM leads, subscriptions, marketing sequences

-- Payment status for cancel
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_payment_status'
    ) THEN
        ALTER TABLE payments
            ADD CONSTRAINT chk_payment_status CHECK (status IN ('ACTIVE', 'CANCELLED'));
    END IF;
END $$;

-- Invoice template document type
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'invoice_templates'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'invoice_templates' AND column_name = 'document_type'
    ) THEN
        ALTER TABLE invoice_templates
            ADD COLUMN document_type VARCHAR(50) NOT NULL DEFAULT 'SALES_INVOICE';
    END IF;
END $$;

-- Payment reminder rules & reminders
CREATE TABLE payment_reminder_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(150) NOT NULL,
    days_offset         INT NOT NULL,
    offset_type         VARCHAR(20) NOT NULL DEFAULT 'AFTER_DUE',
    channel             VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    subject_template    VARCHAR(255),
    body_template       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_reminder_offset_type CHECK (offset_type IN ('BEFORE_DUE', 'AFTER_DUE', 'ON_DUE')),
    CONSTRAINT chk_reminder_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'IN_APP'))
);

CREATE TABLE payment_reminders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_id          UUID NOT NULL REFERENCES sales_invoices(id) ON DELETE CASCADE,
    rule_id             UUID REFERENCES payment_reminder_rules(id) ON DELETE SET NULL,
    channel             VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    recipient           VARCHAR(255),
    subject             VARCHAR(255),
    body                TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    scheduled_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_reminder_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_payment_reminder_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'IN_APP'))
);

CREATE INDEX idx_payment_reminder_rules_org ON payment_reminder_rules(organization_id);
CREATE INDEX idx_payment_reminders_org ON payment_reminders(organization_id, status);
CREATE INDEX idx_payment_reminders_invoice ON payment_reminders(invoice_id);

-- CRM leads
CREATE TABLE leads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    lead_name           VARCHAR(200) NOT NULL,
    company_name        VARCHAR(200),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    source              VARCHAR(100),
    status              VARCHAR(30) NOT NULL DEFAULT 'NEW',
    assigned_to         UUID REFERENCES users(id),
    notes               TEXT,
    estimated_value     NUMERIC(18,2),
    converted_customer_id UUID REFERENCES customers(id),
    converted_at        TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_lead_status CHECK (status IN (
        'NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'WON', 'LOST', 'CONVERTED'
    ))
);

CREATE TABLE lead_follow_ups (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    lead_id             UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    follow_up_at        TIMESTAMPTZ NOT NULL,
    notes               TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    completed_at        TIMESTAMPTZ,
    created_by          UUID REFERENCES users(id),
    updated_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_lead_follow_up_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_leads_org ON leads(organization_id, status);
CREATE INDEX idx_leads_assigned ON leads(assigned_to);
CREATE INDEX idx_lead_follow_ups_lead ON lead_follow_ups(lead_id, follow_up_at);
CREATE INDEX idx_lead_follow_ups_org ON lead_follow_ups(organization_id, status);

-- Subscription plans & user subscriptions
CREATE TABLE subscription_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(50) NOT NULL UNIQUE,
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(255),
    max_organizations       INT NOT NULL DEFAULT 1,
    max_users_per_org       INT NOT NULL DEFAULT 3,
    max_invoices_per_month  INT NOT NULL DEFAULT 50,
    price_monthly           NUMERIC(18,2) NOT NULL DEFAULT 0,
    features_json           JSONB NOT NULL DEFAULT '{}'::jsonb,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id             UUID NOT NULL REFERENCES subscription_plans(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    starts_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_subscriptions_user UNIQUE (user_id),
    CONSTRAINT chk_user_subscription_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'TRIAL'))
);

CREATE INDEX idx_user_subscriptions_plan ON user_subscriptions(plan_id);

INSERT INTO subscription_plans (id, code, name, description, max_organizations, max_users_per_org, max_invoices_per_month, price_monthly, features_json)
VALUES
    (gen_random_uuid(), 'FREE', 'Free', 'Starter free tier', 1, 2, 25, 0,
     '{"reminders":false,"marketing":false,"crm":true}'::jsonb),
    (gen_random_uuid(), 'STARTER', 'Starter', 'Growing businesses', 2, 5, 200, 499,
     '{"reminders":true,"marketing":false,"crm":true}'::jsonb),
    (gen_random_uuid(), 'BUSINESS', 'Business', 'Generous plan for existing users', 20, 100, 10000, 1999,
     '{"reminders":true,"marketing":true,"crm":true}'::jsonb);

-- Assign all existing users to BUSINESS so nothing breaks
INSERT INTO user_subscriptions (user_id, plan_id, status, starts_at)
SELECT u.id, p.id, 'ACTIVE', NOW()
FROM users u
CROSS JOIN subscription_plans p
WHERE p.code = 'BUSINESS'
  AND NOT EXISTS (SELECT 1 FROM user_subscriptions us WHERE us.user_id = u.id);

-- Marketing sequences
CREATE TABLE marketing_sequences (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    trigger_type        VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_marketing_sequence_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT chk_marketing_trigger CHECK (trigger_type IN ('MANUAL', 'LEAD_CREATED', 'CUSTOMER_CREATED', 'INVOICE_OVERDUE'))
);

CREATE TABLE marketing_sequence_steps (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_id         UUID NOT NULL REFERENCES marketing_sequences(id) ON DELETE CASCADE,
    step_order          INT NOT NULL,
    delay_days          INT NOT NULL DEFAULT 0,
    channel             VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    subject_template    VARCHAR(255),
    body_template       TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketing_step_order UNIQUE (sequence_id, step_order),
    CONSTRAINT chk_marketing_step_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'IN_APP'))
);

CREATE TABLE marketing_enrollments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    sequence_id         UUID NOT NULL REFERENCES marketing_sequences(id) ON DELETE CASCADE,
    recipient_type      VARCHAR(30) NOT NULL,
    recipient_id        UUID NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(30),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    current_step        INT NOT NULL DEFAULT 0,
    enrolled_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marketing_enrollment_status CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_marketing_recipient_type CHECK (recipient_type IN ('LEAD', 'CUSTOMER', 'CONTACT'))
);

CREATE TABLE marketing_sends (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    enrollment_id       UUID NOT NULL REFERENCES marketing_enrollments(id) ON DELETE CASCADE,
    step_id             UUID NOT NULL REFERENCES marketing_sequence_steps(id) ON DELETE CASCADE,
    channel             VARCHAR(30) NOT NULL,
    recipient           VARCHAR(255),
    subject             VARCHAR(255),
    body                TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    scheduled_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marketing_send_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_marketing_send_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'IN_APP'))
);

CREATE INDEX idx_marketing_sequences_org ON marketing_sequences(organization_id, status);
CREATE INDEX idx_marketing_enrollments_seq ON marketing_enrollments(sequence_id, status);
CREATE INDEX idx_marketing_sends_enrollment ON marketing_sends(enrollment_id);

-- Optional in-app notifications
CREATE TABLE IF NOT EXISTS in_app_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID REFERENCES organizations(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title               VARCHAR(255) NOT NULL,
    body                TEXT,
    notification_type   VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    entity_type         VARCHAR(100),
    entity_id           UUID,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_in_app_notifications_user ON in_app_notifications(user_id, created_at DESC);

-- Lead permissions
INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), 'LEAD_READ', 'View leads', 'LEAD'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'LEAD_READ');

INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), 'LEAD_WRITE', 'Manage leads', 'LEAD'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'LEAD_WRITE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ORGANIZATION_ADMIN', 'SALES_MANAGER')
  AND p.code IN ('LEAD_READ', 'LEAD_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
