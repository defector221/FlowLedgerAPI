-- Multi-branch / multi-store location hierarchy foundation

-- Extend branches
ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS gst_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pan VARCHAR(20),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_head_office BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE branches SET is_head_office = is_default WHERE is_head_office = FALSE AND is_default = TRUE;

-- Extend warehouses
ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS store_id UUID,
    ADD COLUMN IF NOT EXISTS warehouse_type VARCHAR(20) NOT NULL DEFAULT 'CENTRAL';

ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS chk_warehouses_type;
ALTER TABLE warehouses ADD CONSTRAINT chk_warehouses_type
    CHECK (warehouse_type IN ('CENTRAL', 'BRANCH', 'STORE', 'TRANSIT'));

CREATE INDEX IF NOT EXISTS idx_warehouses_org_branch ON warehouses(organization_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_org_store ON warehouses(organization_id, store_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_org_type ON warehouses(organization_id, warehouse_type);

-- Extend retail_stores (Store entity)
ALTER TABLE retail_stores
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS manager_id UUID,
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS country VARCHAR(100) DEFAULT 'IN',
    ADD COLUMN IF NOT EXISTS store_type VARCHAR(30) DEFAULT 'RETAIL',
    ADD COLUMN IF NOT EXISTS default_price_list_id UUID,
    ADD COLUMN IF NOT EXISTS default_tax_profile_id UUID,
    ADD COLUMN IF NOT EXISTS default_currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS opening_hours JSONB,
    ADD COLUMN IF NOT EXISTS allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS allow_offline_pos BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS enable_click_and_collect BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS enable_loyalty BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS enable_gift_card BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE retail_stores DROP CONSTRAINT IF EXISTS chk_retail_stores_type;
ALTER TABLE retail_stores ADD CONSTRAINT chk_retail_stores_type
    CHECK (store_type IN (
        'RETAIL', 'WHOLESALE', 'ONLINE', 'FRANCHISE', 'DARK_STORE',
        'KIOSK', 'POPUP', 'DISTRIBUTION'
    ));

CREATE INDEX IF NOT EXISTS idx_retail_stores_branch ON retail_stores(organization_id, branch_id)
    WHERE deleted = FALSE;

-- Add store FK on warehouses after retail_stores exists
ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS fk_warehouses_store;
ALTER TABLE warehouses ADD CONSTRAINT fk_warehouses_store
    FOREIGN KEY (store_id) REFERENCES retail_stores(id) ON DELETE SET NULL;

-- Extend retail_terminals
ALTER TABLE retail_terminals
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(100);

-- Cash drawers (physical drawer registry per terminal)
CREATE TABLE IF NOT EXISTS cash_drawers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    terminal_id     UUID NOT NULL REFERENCES retail_terminals(id) ON DELETE CASCADE,
    drawer_code     VARCHAR(50) NOT NULL,
    drawer_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_cash_drawers_terminal_code UNIQUE (terminal_id, drawer_code)
);

CREATE INDEX idx_cash_drawers_org ON cash_drawers(organization_id) WHERE deleted = FALSE;
CREATE INDEX idx_cash_drawers_terminal ON cash_drawers(terminal_id) WHERE deleted = FALSE;

-- Link shifts to cash drawer
ALTER TABLE retail_shifts
    ADD COLUMN IF NOT EXISTS drawer_id UUID REFERENCES cash_drawers(id) ON DELETE SET NULL;
