CREATE TABLE IF NOT EXISTS product_qr_codes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id       UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    payload_template VARCHAR(500) NOT NULL DEFAULT '{{productUrl}}',
    payload_resolved TEXT,
    format           VARCHAR(20) NOT NULL DEFAULT 'PNG',
    object_key_png   VARCHAR(500),
    object_key_svg   VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX IF NOT EXISTS idx_product_qr_codes_product
    ON product_qr_codes (organization_id, product_id);

CREATE TABLE IF NOT EXISTS product_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    object_key      VARCHAR(500) NOT NULL,
    thumbnail_key   VARCHAR(500),
    sort_order      INT NOT NULL DEFAULT 0,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    mime_type       VARCHAR(100),
    size_bytes      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX IF NOT EXISTS idx_product_images_product
    ON product_images (organization_id, product_id, sort_order);
