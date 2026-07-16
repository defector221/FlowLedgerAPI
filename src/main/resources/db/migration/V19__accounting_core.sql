-- Double-entry accounting foundation

CREATE TABLE accounts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    account_code            VARCHAR(50) NOT NULL,
    account_name            VARCHAR(200) NOT NULL,
    account_type            VARCHAR(30) NOT NULL,
    account_sub_type        VARCHAR(50),
    parent_account_id       UUID REFERENCES accounts(id) ON DELETE SET NULL,
    system_account_key      VARCHAR(50),
    system_account          BOOLEAN NOT NULL DEFAULT FALSE,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    allow_manual_posting    BOOLEAN NOT NULL DEFAULT TRUE,
    opening_debit           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    opening_credit          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    CONSTRAINT uq_accounts_org_code UNIQUE (organization_id, account_code),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    CONSTRAINT chk_accounts_opening CHECK (opening_debit >= 0 AND opening_credit >= 0)
);

CREATE UNIQUE INDEX uq_accounts_org_system_key
    ON accounts (organization_id, system_account_key)
    WHERE system_account_key IS NOT NULL;

CREATE INDEX idx_accounts_org_type ON accounts (organization_id, account_type);
CREATE INDEX idx_accounts_org_parent ON accounts (organization_id, parent_account_id);

CREATE TABLE fiscal_years (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(50) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_current          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_fiscal_years_status CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED')),
    CONSTRAINT chk_fiscal_years_dates CHECK (end_date > start_date),
    CONSTRAINT uq_fiscal_years_org_name UNIQUE (organization_id, name)
);

CREATE INDEX idx_fiscal_years_org ON fiscal_years (organization_id, start_date, end_date);
CREATE UNIQUE INDEX uq_fiscal_years_org_current
    ON fiscal_years (organization_id)
    WHERE is_current = TRUE;

CREATE TABLE accounting_periods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    fiscal_year_id      UUID NOT NULL REFERENCES fiscal_years(id) ON DELETE CASCADE,
    period_number       INT NOT NULL,
    name                VARCHAR(50) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_accounting_periods_status CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED')),
    CONSTRAINT chk_accounting_periods_dates CHECK (end_date >= start_date),
    CONSTRAINT uq_accounting_periods_fy_num UNIQUE (fiscal_year_id, period_number)
);

CREATE INDEX idx_accounting_periods_org ON accounting_periods (organization_id, start_date, end_date);

CREATE TABLE journal_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    fiscal_year_id      UUID NOT NULL REFERENCES fiscal_years(id),
    accounting_period_id UUID NOT NULL REFERENCES accounting_periods(id),
    entry_number        VARCHAR(50) NOT NULL,
    entry_date          DATE NOT NULL,
    posting_date        DATE,
    reference_type      VARCHAR(50),
    reference_id        UUID,
    voucher_type        VARCHAR(30) NOT NULL,
    voucher_number      VARCHAR(50),
    description         VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source              VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    reversal_of_id      UUID REFERENCES journal_entries(id),
    reversed_by_id      UUID REFERENCES journal_entries(id),
    total_debit         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_credit        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    posted_at           TIMESTAMPTZ,
    posted_by           UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_journal_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED', 'CANCELLED')),
    CONSTRAINT chk_journal_source CHECK (source IN (
        'MANUAL', 'SALES_INVOICE', 'PURCHASE_INVOICE', 'SALES_RETURN', 'PURCHASE_RETURN',
        'CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT', 'EXPENSE', 'INVENTORY', 'SYSTEM',
        'CREDIT_NOTE', 'DEBIT_NOTE'
    )),
    CONSTRAINT chk_journal_voucher CHECK (voucher_type IN (
        'JOURNAL', 'SALES', 'PURCHASE', 'RECEIPT', 'PAYMENT', 'CONTRA', 'CREDIT_NOTE', 'DEBIT_NOTE'
    )),
    CONSTRAINT uq_journal_entries_org_number UNIQUE (organization_id, entry_number)
);

CREATE UNIQUE INDEX uq_journal_entries_org_source_ref
    ON journal_entries (organization_id, source, reference_id)
    WHERE reference_id IS NOT NULL AND source <> 'MANUAL';

CREATE INDEX idx_journal_entries_org_date ON journal_entries (organization_id, entry_date);
CREATE INDEX idx_journal_entries_org_status ON journal_entries (organization_id, status);
CREATE INDEX idx_journal_entries_source ON journal_entries (organization_id, source, reference_id);

CREATE TABLE journal_entry_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    journal_entry_id    UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id          UUID NOT NULL REFERENCES accounts(id),
    line_number         INT NOT NULL,
    description         VARCHAR(500),
    debit_amount        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit_amount       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    customer_id         UUID REFERENCES customers(id),
    supplier_id         UUID REFERENCES suppliers(id),
    cost_center_id      UUID,
    reference           VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_jel_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0),
    CONSTRAINT chk_jel_one_sided CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0)
    ),
    CONSTRAINT uq_jel_entry_line UNIQUE (journal_entry_id, line_number)
);

CREATE INDEX idx_jel_entry ON journal_entry_lines (journal_entry_id);
CREATE INDEX idx_jel_account ON journal_entry_lines (account_id);
CREATE INDEX idx_jel_customer ON journal_entry_lines (customer_id);
CREATE INDEX idx_jel_supplier ON journal_entry_lines (supplier_id);
CREATE INDEX idx_jel_org ON journal_entry_lines (organization_id);

-- Accounting permissions
INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), 'ACCOUNTING_READ', 'View accounting', 'ACCOUNTING'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ACCOUNTING_READ');

INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), 'ACCOUNTING_WRITE', 'Manage accounting', 'ACCOUNTING'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ACCOUNTING_WRITE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ORGANIZATION_ADMIN', 'ACCOUNTANT')
  AND p.code IN ('ACCOUNTING_READ', 'ACCOUNTING_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
