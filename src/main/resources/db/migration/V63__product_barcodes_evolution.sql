-- Evolve retail_product_barcodes for enterprise barcode management + history audit trail

ALTER TABLE retail_product_barcodes
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS is_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE retail_product_barcodes
    DROP CONSTRAINT IF EXISTS chk_retail_product_barcodes_status;

ALTER TABLE retail_product_barcodes
    ADD CONSTRAINT chk_retail_product_barcodes_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_retail_barcodes_org_value_active
    ON retail_product_barcodes (organization_id, lower(barcode))
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS product_barcode_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    barcode_id      UUID REFERENCES retail_product_barcodes(id) ON DELETE SET NULL,
    old_barcode     VARCHAR(150),
    new_barcode     VARCHAR(150),
    operation       VARCHAR(30) NOT NULL,
    reason          VARCHAR(500),
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_product_barcode_history_op CHECK (
        operation IN ('GENERATED', 'UPDATED', 'REGENERATED', 'DELETED', 'ALIAS_ADDED')
    )
);

CREATE INDEX IF NOT EXISTS idx_product_barcode_history_product
    ON product_barcode_history (organization_id, product_id, created_at DESC);

ALTER TABLE supplier_catalog_items
    ADD COLUMN IF NOT EXISTS supplier_barcode VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_supplier_catalog_supplier_barcode
    ON supplier_catalog_items (organization_id, lower(supplier_barcode))
    WHERE deleted = FALSE AND supplier_barcode IS NOT NULL;
