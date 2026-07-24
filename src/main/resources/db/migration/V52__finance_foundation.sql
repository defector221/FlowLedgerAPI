-- Wave 1: Finance foundation — masters, voucher engine, ledger balances

CREATE TABLE IF NOT EXISTS branches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    address_line1       VARCHAR(255),
    city                VARCHAR(100),
    state               VARCHAR(100),
    postal_code         VARCHAR(30),
    country             VARCHAR(100) DEFAULT 'IN',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_branches_org_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_branches_org ON branches (organization_id);

CREATE TABLE IF NOT EXISTS cost_centers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_cost_centers_org_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS departments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_departments_org_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS currencies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code                VARCHAR(10) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    symbol              VARCHAR(10),
    decimal_places      INT NOT NULL DEFAULT 2,
    is_base             BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_currencies_org_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS exchange_rates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    from_currency       VARCHAR(10) NOT NULL,
    to_currency         VARCHAR(10) NOT NULL,
    rate                NUMERIC(19, 8) NOT NULL,
    effective_date      DATE NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_exchange_rates UNIQUE (organization_id, from_currency, to_currency, effective_date),
    CONSTRAINT chk_exchange_rates_positive CHECK (rate > 0)
);

CREATE TABLE IF NOT EXISTS vouchers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    branch_id               UUID REFERENCES branches(id) ON DELETE SET NULL,
    voucher_number          VARCHAR(50) NOT NULL,
    voucher_type            VARCHAR(40) NOT NULL,
    voucher_date            DATE NOT NULL,
    currency_code           VARCHAR(10) NOT NULL DEFAULT 'INR',
    exchange_rate           NUMERIC(19, 8) NOT NULL DEFAULT 1,
    reference_type          VARCHAR(100),
    reference_id            UUID,
    narration               TEXT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_debit             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_credit            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    posted                  BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at               TIMESTAMPTZ,
    posted_by               UUID,
    journal_entry_id        UUID REFERENCES journal_entries(id) ON DELETE SET NULL,
    reversed_voucher_id     UUID REFERENCES vouchers(id) ON DELETE SET NULL,
    reversal_of_id          UUID REFERENCES vouchers(id) ON DELETE SET NULL,
    is_recurring            BOOLEAN NOT NULL DEFAULT FALSE,
    recurring_template_id   UUID,
    recurrence_rule         TEXT,
    version                 BIGINT NOT NULL DEFAULT 0,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    CONSTRAINT uq_vouchers_org_number UNIQUE (organization_id, voucher_number),
    CONSTRAINT chk_vouchers_status CHECK (status IN ('DRAFT', 'APPROVED', 'POSTED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT chk_vouchers_balanced CHECK (total_debit = total_credit)
);

CREATE INDEX idx_vouchers_org_date ON vouchers (organization_id, voucher_date DESC);
CREATE INDEX idx_vouchers_org_status ON vouchers (organization_id, status);
CREATE INDEX idx_vouchers_org_type ON vouchers (organization_id, voucher_type);
CREATE INDEX idx_vouchers_org_ref ON vouchers (organization_id, reference_type, reference_id);

CREATE TABLE IF NOT EXISTS voucher_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    voucher_id              UUID NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id),
    debit                   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit                  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    description             VARCHAR(500),
    cost_center_id          UUID REFERENCES cost_centers(id) ON DELETE SET NULL,
    department_id           UUID REFERENCES departments(id) ON DELETE SET NULL,
    project_id              UUID,
    warehouse_id            UUID,
    inventory_reference     VARCHAR(200),
    tax_rate_id             UUID,
    tax_amount              NUMERIC(19, 4) NOT NULL DEFAULT 0,
    sort_order              INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    CONSTRAINT chk_voucher_lines_one_sided CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0) OR (debit = 0 AND credit = 0)
    )
);

CREATE INDEX idx_voucher_lines_voucher ON voucher_lines (voucher_id, sort_order);

CREATE TABLE IF NOT EXISTS voucher_sequences (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    branch_id           UUID,
    voucher_type        VARCHAR(40) NOT NULL,
    financial_year      VARCHAR(20) NOT NULL,
    prefix              VARCHAR(50) NOT NULL,
    next_number         BIGINT NOT NULL DEFAULT 1,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_voucher_sequences
    ON voucher_sequences (organization_id, voucher_type, financial_year, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'));

CREATE TABLE IF NOT EXISTS ledger_balances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    account_id          UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    fiscal_year_id      UUID NOT NULL REFERENCES fiscal_years(id) ON DELETE CASCADE,
    accounting_period_id UUID REFERENCES accounting_periods(id) ON DELETE CASCADE,
    debit_total         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit_total        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    balance             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_balances UNIQUE (organization_id, account_id, fiscal_year_id, accounting_period_id)
);

CREATE INDEX idx_ledger_balances_org_account ON ledger_balances (organization_id, account_id);

-- Expand accounting voucher types used by journals
-- (journal_entries.voucher_type is VARCHAR via enum STRING — no DDL change required)

INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), v.code, v.name, v.module
FROM (VALUES
    ('VOUCHER_READ', 'View vouchers', 'ACCOUNTING'),
    ('VOUCHER_WRITE', 'Create and edit vouchers', 'ACCOUNTING'),
    ('VOUCHER_APPROVE', 'Approve vouchers', 'ACCOUNTING'),
    ('VOUCHER_POST', 'Post vouchers to ledger', 'ACCOUNTING'),
    ('BRANCH_MANAGE', 'Manage branches', 'SETTINGS'),
    ('DIMENSION_MANAGE', 'Manage cost centers and departments', 'ACCOUNTING')
) AS v(code, name, module)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code IN ('VOUCHER_READ','VOUCHER_WRITE','VOUCHER_APPROVE','VOUCHER_POST','BRANCH_MANAGE','DIMENSION_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('VOUCHER_READ','VOUCHER_WRITE','VOUCHER_APPROVE','VOUCHER_POST','DIMENSION_MANAGE')
WHERE r.code = 'ACCOUNTANT'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Backfill ledger balances from posted journal lines (period-level)
INSERT INTO ledger_balances (id, organization_id, account_id, fiscal_year_id, accounting_period_id, debit_total, credit_total, balance, updated_at)
SELECT gen_random_uuid(),
       je.organization_id,
       jel.account_id,
       je.fiscal_year_id,
       je.accounting_period_id,
       COALESCE(SUM(jel.debit_amount), 0),
       COALESCE(SUM(jel.credit_amount), 0),
       COALESCE(SUM(jel.debit_amount), 0) - COALESCE(SUM(jel.credit_amount), 0),
       NOW()
FROM journal_entries je
JOIN journal_entry_lines jel ON jel.journal_entry_id = je.id
WHERE je.status = 'POSTED'
GROUP BY je.organization_id, jel.account_id, je.fiscal_year_id, je.accounting_period_id
ON CONFLICT ON CONSTRAINT uq_ledger_balances DO NOTHING;
