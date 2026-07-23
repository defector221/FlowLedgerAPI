-- Retail inventory locations, labels, employee commission hooks, offline sync outbox

CREATE TABLE retail_inventory_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES retail_stores(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    location_type VARCHAR(30) NOT NULL DEFAULT 'SHELF',
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_inv_locations_store_code UNIQUE (store_id, code)
);

CREATE TABLE retail_stock_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES retail_stores(id),
    location_id UUID REFERENCES retail_inventory_locations(id),
    count_type VARCHAR(30) NOT NULL DEFAULT 'CYCLE',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    counted_at TIMESTAMPTZ,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE retail_stock_count_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    count_id UUID NOT NULL REFERENCES retail_stock_counts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    system_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    counted_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    variance_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE retail_label_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    label_type VARCHAR(40) NOT NULL DEFAULT 'SHELF',
    template_body TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_label_templates_org_code UNIQUE (organization_id, code)
);

CREATE TABLE retail_commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    rate_percent NUMERIC(8, 4) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE retail_pos_sync_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID,
    client_id VARCHAR(100) NOT NULL,
    client_txn_id VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_retail_pos_sync_client_txn UNIQUE (organization_id, client_id, client_txn_id)
);

CREATE INDEX idx_retail_pos_sync_pending ON retail_pos_sync_outbox(organization_id, status);
