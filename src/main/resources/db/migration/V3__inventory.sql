-- Warehouses and inventory ledger

CREATE TABLE warehouses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    warehouse_code  VARCHAR(50) NOT NULL,
    warehouse_name  VARCHAR(200) NOT NULL,
    address         TEXT,
    contact_person  VARCHAR(150),
    phone           VARCHAR(30),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_warehouses_org_code UNIQUE (organization_id, warehouse_code)
);

ALTER TABLE organization_settings
    ADD CONSTRAINT fk_settings_default_wh FOREIGN KEY (default_warehouse_id) REFERENCES warehouses(id);

CREATE TABLE inventory_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    warehouse_id        UUID NOT NULL REFERENCES warehouses(id),
    transaction_type    VARCHAR(40) NOT NULL,
    transaction_date    DATE NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        UUID,
    reference_number    VARCHAR(100),
    inward_qty          NUMERIC(18,4) NOT NULL DEFAULT 0,
    outward_qty         NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit_cost           NUMERIC(18,4),
    batch_number        VARCHAR(100),
    serial_number       VARCHAR(100),
    expiry_date         DATE,
    notes               TEXT,
    idempotency_key     VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    CONSTRAINT chk_inv_qty CHECK (inward_qty >= 0 AND outward_qty >= 0),
    CONSTRAINT chk_inv_type CHECK (transaction_type IN (
        'OPENING_STOCK','PURCHASE','PURCHASE_RETURN','SALE','SALES_RETURN',
        'STOCK_ADJUSTMENT','STOCK_TRANSFER','DAMAGED','EXPIRED'
    ))
);

CREATE UNIQUE INDEX uq_inv_idempotency ON inventory_transactions(organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE inventory_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    warehouse_id    UUID NOT NULL REFERENCES warehouses(id),
    batch_number    VARCHAR(100) NOT NULL,
    expiry_date     DATE,
    quantity        NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_batch UNIQUE (organization_id, product_id, warehouse_id, batch_number)
);

CREATE TABLE serial_numbers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    warehouse_id    UUID REFERENCES warehouses(id),
    serial_number   VARCHAR(150) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_serial UNIQUE (organization_id, product_id, serial_number),
    CONSTRAINT chk_serial_status CHECK (status IN ('IN_STOCK','SOLD','DAMAGED','RETURNED'))
);

CREATE INDEX idx_inv_txn_org_product ON inventory_transactions(organization_id, product_id, transaction_date);
CREATE INDEX idx_inv_txn_warehouse ON inventory_transactions(organization_id, warehouse_id);
CREATE INDEX idx_inv_txn_reference ON inventory_transactions(organization_id, reference_type, reference_id);
