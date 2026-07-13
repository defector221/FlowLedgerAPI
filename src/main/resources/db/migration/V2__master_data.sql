-- Master data: customers, suppliers, categories, units, products, tax rates

CREATE TABLE tax_rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    rate            NUMERIC(8,4) NOT NULL,
    cgst_rate       NUMERIC(8,4) NOT NULL DEFAULT 0,
    sgst_rate       NUMERIC(8,4) NOT NULL DEFAULT 0,
    igst_rate       NUMERIC(8,4) NOT NULL DEFAULT 0,
    cess_rate       NUMERIC(8,4) NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_tax_rates_org_name UNIQUE (organization_id, name)
);

ALTER TABLE organizations
    ADD CONSTRAINT fk_org_default_tax FOREIGN KEY (default_tax_rate_id) REFERENCES tax_rates(id);

CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_code       VARCHAR(50) NOT NULL,
    customer_name       VARCHAR(200) NOT NULL,
    company_name        VARCHAR(200),
    gstin               VARCHAR(20),
    pan                 VARCHAR(20),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    billing_address     TEXT,
    shipping_address    TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    state_code          VARCHAR(10),
    country             VARCHAR(100) NOT NULL DEFAULT 'India',
    credit_limit        NUMERIC(18,2) NOT NULL DEFAULT 0,
    payment_terms       VARCHAR(255),
    opening_balance     NUMERIC(18,2) NOT NULL DEFAULT 0,
    notes               TEXT,
    archived            BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_customers_org_code UNIQUE (organization_id, customer_code)
);

CREATE TABLE suppliers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    supplier_code       VARCHAR(50) NOT NULL,
    supplier_name       VARCHAR(200) NOT NULL,
    company_name        VARCHAR(200),
    gstin               VARCHAR(20),
    pan                 VARCHAR(20),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    billing_address     TEXT,
    shipping_address    TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    state_code          VARCHAR(10),
    country             VARCHAR(100) NOT NULL DEFAULT 'India',
    payment_terms       VARCHAR(255),
    opening_balance     NUMERIC(18,2) NOT NULL DEFAULT 0,
    bank_name           VARCHAR(200),
    bank_account_number VARCHAR(50),
    bank_ifsc           VARCHAR(20),
    notes               TEXT,
    archived            BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_suppliers_org_code UNIQUE (organization_id, supplier_code)
);

CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    parent_id       UUID REFERENCES categories(id),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_categories_org_name UNIQUE (organization_id, name)
);

CREATE TABLE units (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    code            VARCHAR(30) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    system_unit     BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_units_org_code UNIQUE (organization_id, code)
);

CREATE UNIQUE INDEX uq_units_system_code ON units(code) WHERE organization_id IS NULL;

CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    item_type           VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    sku                 VARCHAR(100) NOT NULL,
    barcode             VARCHAR(100),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    category_id         UUID REFERENCES categories(id),
    brand               VARCHAR(150),
    hsn_sac_code        VARCHAR(20),
    unit_id             UUID NOT NULL REFERENCES units(id),
    purchase_price      NUMERIC(18,4) NOT NULL DEFAULT 0,
    selling_price       NUMERIC(18,4) NOT NULL DEFAULT 0,
    mrp                 NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_rate_id         UUID REFERENCES tax_rates(id),
    opening_stock       NUMERIC(18,4) NOT NULL DEFAULT 0,
    minimum_stock_level NUMERIC(18,4) NOT NULL DEFAULT 0,
    maximum_stock_level NUMERIC(18,4),
    reorder_level       NUMERIC(18,4) NOT NULL DEFAULT 0,
    batch_tracking      BOOLEAN NOT NULL DEFAULT FALSE,
    serial_tracking     BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_tracking     BOOLEAN NOT NULL DEFAULT FALSE,
    image_object_key    VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_products_org_sku UNIQUE (organization_id, sku),
    CONSTRAINT chk_products_item_type CHECK (item_type IN ('PRODUCT', 'SERVICE'))
);

CREATE INDEX idx_customers_org_name ON customers(organization_id, customer_name);
CREATE INDEX idx_suppliers_org_name ON suppliers(organization_id, supplier_name);
CREATE INDEX idx_products_org_name ON products(organization_id, name);
CREATE INDEX idx_products_barcode ON products(organization_id, barcode);
