-- Epic 4.3: Scan & Go

CREATE TABLE commerce_scan_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18, 4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    item_count          INT NOT NULL DEFAULT 0,
    entry_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at             TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    commerce_order_id   UUID REFERENCES commerce_orders(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scan_sessions_store_status ON commerce_scan_sessions(store_id, status);
CREATE INDEX idx_scan_sessions_customer ON commerce_scan_sessions(customer_id, status);

CREATE TABLE commerce_scan_session_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES commerce_scan_sessions(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    quantity            NUMERIC(18, 4) NOT NULL DEFAULT 1,
    line_subtotal       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_tax            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    product_snapshot    JSONB NOT NULL DEFAULT '{}',
    price_snapshot      JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scan_session_product UNIQUE (session_id, product_id)
);

CREATE TABLE commerce_scan_exit_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL UNIQUE REFERENCES commerce_scan_sessions(id) ON DELETE CASCADE,
    token_hash          VARCHAR(128) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    verified_at         TIMESTAMPTZ,
    verified_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
