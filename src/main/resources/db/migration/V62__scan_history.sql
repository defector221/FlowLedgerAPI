CREATE TABLE IF NOT EXISTS scan_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    barcode             VARCHAR(150) NOT NULL,
    source              VARCHAR(20) NOT NULL DEFAULT 'SCANNER',
    module              VARCHAR(60),
    resolved_product_id UUID REFERENCES products(id),
    resolved_variant_id UUID,
    success             BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason      VARCHAR(100),
    scanned_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    CONSTRAINT chk_scan_history_source CHECK (
        source IN ('SCANNER', 'MANUAL', 'CAMERA')
    )
);

CREATE INDEX IF NOT EXISTS idx_scan_history_org_scanned
    ON scan_history (organization_id, scanned_at DESC);

CREATE INDEX IF NOT EXISTS idx_scan_history_org_barcode
    ON scan_history (organization_id, barcode);
