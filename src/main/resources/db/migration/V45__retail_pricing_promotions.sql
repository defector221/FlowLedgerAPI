-- Pricing lists and promotions

CREATE TABLE retail_price_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    price_type VARCHAR(40) NOT NULL DEFAULT 'RETAIL',
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_price_lists_org_code UNIQUE (organization_id, code)
);

CREATE TABLE retail_price_list_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    price_list_id UUID NOT NULL REFERENCES retail_price_lists(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id UUID,
    unit_price NUMERIC(18, 4) NOT NULL,
    min_qty NUMERIC(18, 4) NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE retail_store_price_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES retail_stores(id) ON DELETE CASCADE,
    price_list_id UUID NOT NULL REFERENCES retail_price_lists(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_retail_store_price_list UNIQUE (store_id, price_list_id)
);

CREATE TABLE retail_promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    promo_type VARCHAR(40) NOT NULL,
    discount_percent NUMERIC(8, 4),
    discount_amount NUMERIC(18, 2),
    buy_qty NUMERIC(18, 4),
    get_qty NUMERIC(18, 4),
    min_bill_amount NUMERIC(18, 2),
    coupon_code VARCHAR(50),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    store_id UUID REFERENCES retail_stores(id),
    brand_id UUID REFERENCES retail_brands(id),
    category_id UUID,
    product_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_promotions_org_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_retail_promotions_coupon ON retail_promotions(organization_id, coupon_code) WHERE deleted = FALSE;
