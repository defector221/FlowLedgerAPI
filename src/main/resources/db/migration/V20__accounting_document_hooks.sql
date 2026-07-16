-- Accounting posting state on operational documents

ALTER TABLE sales_invoices
    ADD COLUMN IF NOT EXISTS accounting_status VARCHAR(20) NOT NULL DEFAULT 'NOT_POSTED',
    ADD COLUMN IF NOT EXISTS posted_journal_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN IF NOT EXISTS accounting_posted_at TIMESTAMPTZ;

ALTER TABLE purchase_invoices
    ADD COLUMN IF NOT EXISTS accounting_status VARCHAR(20) NOT NULL DEFAULT 'NOT_POSTED',
    ADD COLUMN IF NOT EXISTS posted_journal_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN IF NOT EXISTS accounting_posted_at TIMESTAMPTZ;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS accounting_status VARCHAR(20) NOT NULL DEFAULT 'NOT_POSTED',
    ADD COLUMN IF NOT EXISTS posted_journal_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN IF NOT EXISTS accounting_posted_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_si_accounting_status') THEN
        ALTER TABLE sales_invoices
            ADD CONSTRAINT chk_si_accounting_status
            CHECK (accounting_status IN ('NOT_POSTED', 'POSTED', 'REVERSED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pi_accounting_status') THEN
        ALTER TABLE purchase_invoices
            ADD CONSTRAINT chk_pi_accounting_status
            CHECK (accounting_status IN ('NOT_POSTED', 'POSTED', 'REVERSED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pay_accounting_status') THEN
        ALTER TABLE payments
            ADD CONSTRAINT chk_pay_accounting_status
            CHECK (accounting_status IN ('NOT_POSTED', 'POSTED', 'REVERSED'));
    END IF;
END $$;
