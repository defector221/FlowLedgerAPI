-- Barcode module permissions + bulk import job stub

INSERT INTO permissions (id, code, name, module)
VALUES
    (gen_random_uuid(), 'BARCODE_READ', 'View barcodes and scan history', 'BARCODE'),
    (gen_random_uuid(), 'BARCODE_WRITE', 'Manage barcodes, labels, and print jobs', 'BARCODE'),
    (gen_random_uuid(), 'BARCODE_PRINT', 'Print barcode labels', 'BARCODE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('BARCODE_READ', 'BARCODE_WRITE', 'BARCODE_PRINT')
WHERE r.code = 'ORGANIZATION_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('BARCODE_READ', 'BARCODE_PRINT')
WHERE r.code IN ('INVENTORY_MANAGER', 'PURCHASE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS product_import_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    file_name       VARCHAR(255),
    preview_json    JSONB NOT NULL DEFAULT '[]',
    result_json     JSONB NOT NULL DEFAULT '{}',
    error_message   TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_product_import_jobs_status CHECK (
        status IN ('PENDING', 'PREVIEW', 'COMMITTED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_product_import_jobs_org
    ON product_import_jobs (organization_id, created_at DESC);
