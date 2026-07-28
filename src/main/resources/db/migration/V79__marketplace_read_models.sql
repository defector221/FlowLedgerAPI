-- Commerce Epic 2: marketplace read models — typed search columns + facet indexes

-- Evolve marketplace_store_index
ALTER TABLE marketplace_store_index
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS state VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country VARCHAR(3) DEFAULT 'IN',
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS discovery_radius_km NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS search_text TEXT;

CREATE INDEX IF NOT EXISTS idx_marketplace_store_city ON marketplace_store_index(city) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_store_postal ON marketplace_store_index(postal_code) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_store_public ON marketplace_store_index(published, visibility)
    WHERE published = TRUE AND visibility = 'PUBLIC';

-- Evolve marketplace_product_index
ALTER TABLE marketplace_product_index
    ADD COLUMN IF NOT EXISTS sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gtin VARCHAR(20),
    ADD COLUMN IF NOT EXISTS name VARCHAR(500),
    ADD COLUMN IF NOT EXISTS brand VARCHAR(200),
    ADD COLUMN IF NOT EXISTS category_id UUID,
    ADD COLUMN IF NOT EXISTS category_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS price NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS inventory_qty NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS image_urls JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS search_text TEXT;

CREATE INDEX IF NOT EXISTS idx_marketplace_product_published ON marketplace_product_index(published) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_product_store ON marketplace_product_index(store_id);

CREATE INDEX IF NOT EXISTS idx_marketplace_product_barcode ON marketplace_product_index(barcode) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_product_sku ON marketplace_product_index(sku) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_product_brand ON marketplace_product_index(brand) WHERE published = TRUE;
CREATE INDEX IF NOT EXISTS idx_marketplace_product_category ON marketplace_product_index(category_id) WHERE published = TRUE;

DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_marketplace_product_name_trgm ON marketplace_product_index USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_marketplace_store_name_trgm ON marketplace_store_index USING gin (name gin_trgm_ops);

-- Inventory snapshots per store+product
CREATE TABLE marketplace_inventory_index (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id            UUID NOT NULL REFERENCES retail_stores(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_qty       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    content_hash        VARCHAR(64),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_inventory_store_product UNIQUE (store_id, product_id)
);

CREATE INDEX idx_marketplace_inventory_store ON marketplace_inventory_index(store_id) WHERE published = TRUE;

-- Category facets (org-scoped, marketplace-wide listing)
CREATE TABLE marketplace_category_index (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    category_id         UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    parent_id           UUID,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    product_count       INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_category_org UNIQUE (organization_id, category_id)
);

CREATE INDEX idx_marketplace_category_published ON marketplace_category_index(published) WHERE published = TRUE;

-- Brand facets
CREATE TABLE marketplace_brand_index (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    brand_name          VARCHAR(200) NOT NULL,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    product_count       INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_brand_org UNIQUE (organization_id, brand_name)
);

CREATE INDEX idx_marketplace_brand_published ON marketplace_brand_index(published) WHERE published = TRUE;

-- Sync job queue for retry / idempotency
CREATE TABLE marketplace_sync_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID REFERENCES organizations(id) ON DELETE CASCADE,
    job_type            VARCHAR(40) NOT NULL,
    entity_type         VARCHAR(40) NOT NULL,
    entity_id           UUID,
    store_id            UUID,
    sync_mode           VARCHAR(20) NOT NULL DEFAULT 'INCREMENTAL',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts            INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 5,
    idempotency_key     VARCHAR(200) NOT NULL,
    version             BIGINT,
    last_error          TEXT,
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_sync_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_marketplace_sync_pending ON marketplace_sync_jobs(status, scheduled_at)
    WHERE status IN ('PENDING', 'FAILED');
