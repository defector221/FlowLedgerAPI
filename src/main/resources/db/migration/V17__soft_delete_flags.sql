-- Soft-delete flags: never physically remove recoverable master/CRM records
ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_leads_org_archived ON leads(organization_id, archived);

ALTER TABLE email_templates
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE invoice_templates
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Allow reusing names after soft delete (unique only among active rows)
ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS uq_email_templates_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_email_templates_name_active
    ON email_templates (organization_id, lower(name))
    WHERE active = TRUE;

ALTER TABLE invoice_templates DROP CONSTRAINT IF EXISTS uq_invoice_templates_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_templates_name_active
    ON invoice_templates (organization_id, template_name)
    WHERE active = TRUE;
