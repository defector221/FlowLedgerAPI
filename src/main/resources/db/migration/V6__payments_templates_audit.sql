-- Payments, templates, audit logs

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    payment_number      VARCHAR(100) NOT NULL,
    payment_date        DATE NOT NULL,
    payment_type        VARCHAR(30) NOT NULL,
    party_type          VARCHAR(20) NOT NULL,
    customer_id         UUID REFERENCES customers(id),
    supplier_id         UUID REFERENCES suppliers(id),
    amount              NUMERIC(18,2) NOT NULL,
    payment_mode        VARCHAR(40) NOT NULL,
    transaction_reference VARCHAR(150),
    bank_reference      VARCHAR(150),
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_payments_number UNIQUE (organization_id, payment_number),
    CONSTRAINT chk_payment_type CHECK (payment_type IN ('RECEIPT','PAYMENT')),
    CONSTRAINT chk_party_type CHECK (party_type IN ('CUSTOMER','SUPPLIER')),
    CONSTRAINT chk_payment_mode CHECK (payment_mode IN (
        'CASH','BANK_TRANSFER','UPI','CHEQUE','CREDIT_CARD','DEBIT_CARD','PAYMENT_GATEWAY','OTHER'
    ))
);

CREATE TABLE payment_allocations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    document_type       VARCHAR(40) NOT NULL,
    document_id         UUID NOT NULL,
    allocated_amount    NUMERIC(18,2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_alloc_doc_type CHECK (document_type IN ('SALES_INVOICE','PURCHASE_INVOICE','CREDIT_NOTE','DEBIT_NOTE'))
);

CREATE TABLE invoice_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    template_name       VARCHAR(150) NOT NULL,
    preset_key          VARCHAR(50),
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    config_json         JSONB NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_invoice_templates_name UNIQUE (organization_id, template_name)
);

ALTER TABLE sales_invoices
    ADD CONSTRAINT fk_si_template FOREIGN KEY (template_id) REFERENCES invoice_templates(id);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id),
    user_id         UUID REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_org_date ON payments(organization_id, payment_date);
CREATE INDEX idx_payment_alloc_doc ON payment_allocations(document_type, document_id);
CREATE INDEX idx_audit_logs_org ON audit_logs(organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
