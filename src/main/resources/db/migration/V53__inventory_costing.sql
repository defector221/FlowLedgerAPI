-- Wave 5: Inventory costing + Wave 4 CONTRA / opening-balance support

-- ---------------------------------------------------------------------------
-- Journal entry source / voucher-type checks (new finance + inventory sources)
-- ---------------------------------------------------------------------------
ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS chk_journal_source;
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_source CHECK (source IN (
        'MANUAL', 'SALES_INVOICE', 'PURCHASE_INVOICE', 'SALES_RETURN', 'PURCHASE_RETURN',
        'CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT', 'EXPENSE', 'INVENTORY', 'SYSTEM',
        'CREDIT_NOTE', 'DEBIT_NOTE', 'VOUCHER', 'CONTRA', 'OPENING_BALANCE', 'STOCK_ADJUSTMENT'
    ));

ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS chk_journal_voucher;
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_voucher CHECK (voucher_type IN (
        'JOURNAL', 'SALES', 'PURCHASE', 'RECEIPT', 'PAYMENT', 'CONTRA',
        'CREDIT_NOTE', 'DEBIT_NOTE', 'EXPENSE', 'OPENING_BALANCE',
        'PAYROLL', 'ASSET_PURCHASE', 'DEPRECIATION'
    ));

-- ---------------------------------------------------------------------------
-- CONTRA payments: bank/cash transfers between GL accounts
-- ---------------------------------------------------------------------------
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_type;
ALTER TABLE payments
    ADD CONSTRAINT chk_payment_type CHECK (payment_type IN ('RECEIPT', 'PAYMENT', 'CONTRA'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_party_type;
ALTER TABLE payments
    ADD CONSTRAINT chk_party_type CHECK (party_type IN ('CUSTOMER', 'SUPPLIER', 'INTERNAL'));

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS from_account_id UUID REFERENCES accounts(id),
    ADD COLUMN IF NOT EXISTS to_account_id UUID REFERENCES accounts(id);

-- ---------------------------------------------------------------------------
-- Organization inventory costing method
-- ---------------------------------------------------------------------------
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS inventory_costing_method VARCHAR(10) NOT NULL DEFAULT 'WAC';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_org_inventory_costing_method'
    ) THEN
        ALTER TABLE organizations
            ADD CONSTRAINT chk_org_inventory_costing_method
            CHECK (inventory_costing_method IN ('FIFO', 'WAC'));
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Cost layers (FIFO / WAC)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory_cost_layers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    warehouse_id        UUID NOT NULL,
    batch_id            UUID,
    qty_remaining       NUMERIC(18, 4) NOT NULL,
    unit_cost           NUMERIC(19, 6) NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    method              VARCHAR(10) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_cost_layers_qty CHECK (qty_remaining >= 0),
    CONSTRAINT chk_cost_layers_method CHECK (method IN ('FIFO', 'WAC'))
);

CREATE INDEX IF NOT EXISTS idx_cost_layers_org_product_wh
    ON inventory_cost_layers (organization_id, product_id, warehouse_id, received_at);

CREATE INDEX IF NOT EXISTS idx_cost_layers_remaining
    ON inventory_cost_layers (organization_id, product_id, warehouse_id)
    WHERE qty_remaining > 0;

-- ---------------------------------------------------------------------------
-- Stock reservations
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_reservations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    warehouse_id        UUID NOT NULL,
    qty                 NUMERIC(18, 4) NOT NULL,
    reference_type      VARCHAR(60) NOT NULL,
    reference_id        UUID NOT NULL,
    expires_at          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_stock_reservations_qty CHECK (qty > 0),
    CONSTRAINT chk_stock_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_stock_reservations_org_product_wh
    ON stock_reservations (organization_id, product_id, warehouse_id, status);

CREATE INDEX IF NOT EXISTS idx_stock_reservations_ref
    ON stock_reservations (organization_id, reference_type, reference_id);
