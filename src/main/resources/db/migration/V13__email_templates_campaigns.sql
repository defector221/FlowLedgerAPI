-- Shared Unlayer email templates
CREATE TABLE email_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(150) NOT NULL,
    subject             VARCHAR(255) NOT NULL DEFAULT '',
    design_json         JSONB,
    html                TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_email_templates_name UNIQUE (organization_id, name)
);

CREATE INDEX idx_email_templates_org ON email_templates(organization_id);

-- Sequence steps can reference an email template
ALTER TABLE marketing_sequence_steps
    ADD COLUMN email_template_id UUID REFERENCES email_templates(id) ON DELETE SET NULL;

ALTER TABLE marketing_sequence_steps
    ALTER COLUMN body_template DROP NOT NULL;

ALTER TABLE marketing_sequence_steps
    ALTER COLUMN body_template SET DEFAULT '';

-- One-shot blast campaigns
CREATE TABLE marketing_campaigns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(150) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    audience_type       VARCHAR(30) NOT NULL DEFAULT 'LEAD',
    filter_json         JSONB,
    email_template_id   UUID NOT NULL REFERENCES email_templates(id),
    scheduled_at        TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    total_count         INT NOT NULL DEFAULT 0,
    sent_count          INT NOT NULL DEFAULT 0,
    failed_count        INT NOT NULL DEFAULT 0,
    skipped_count       INT NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_campaign_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED')),
    CONSTRAINT chk_campaign_audience CHECK (audience_type IN ('LEAD', 'CUSTOMER', 'MIXED'))
);

CREATE INDEX idx_marketing_campaigns_org ON marketing_campaigns(organization_id, status);
CREATE INDEX idx_marketing_campaigns_due ON marketing_campaigns(status, scheduled_at);

CREATE TABLE marketing_campaign_recipients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id         UUID NOT NULL REFERENCES marketing_campaigns(id) ON DELETE CASCADE,
    recipient_type      VARCHAR(30) NOT NULL,
    recipient_id        UUID NOT NULL,
    email               VARCHAR(255),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message       TEXT,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_campaign_recipient_type CHECK (recipient_type IN ('LEAD', 'CUSTOMER')),
    CONSTRAINT chk_campaign_recipient_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT uq_campaign_recipient UNIQUE (campaign_id, recipient_type, recipient_id)
);

CREATE INDEX idx_campaign_recipients_pending ON marketing_campaign_recipients(campaign_id, status);

-- Invoice / document templates: SECTION (OpenPDF) or UNLAYER (HTML→PDF)
ALTER TABLE invoice_templates
    ADD COLUMN IF NOT EXISTS editor_mode VARCHAR(20) NOT NULL DEFAULT 'SECTION',
    ADD COLUMN IF NOT EXISTS design_json JSONB,
    ADD COLUMN IF NOT EXISTS html TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_invoice_templates_editor_mode'
    ) THEN
        ALTER TABLE invoice_templates
            ADD CONSTRAINT chk_invoice_templates_editor_mode
            CHECK (editor_mode IN ('SECTION', 'UNLAYER'));
    END IF;
END $$;
