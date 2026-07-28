CREATE TABLE commerce_order_returns (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    commerce_order_id       UUID NOT NULL REFERENCES commerce_orders(id) ON DELETE CASCADE,
    fulfillment_order_id    UUID REFERENCES commerce_fulfillment_orders(id) ON DELETE SET NULL,
    erp_sales_return_id     UUID,
    return_number           VARCHAR(64),
    status                  VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_order_returns_order ON commerce_order_returns(commerce_order_id);

CREATE TABLE commerce_order_return_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id           UUID NOT NULL REFERENCES commerce_order_returns(id) ON DELETE CASCADE,
    order_line_id       UUID NOT NULL REFERENCES commerce_order_lines(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    quantity            NUMERIC(18, 4) NOT NULL,
    rate                NUMERIC(18, 4) NOT NULL,
    line_total          NUMERIC(18, 4) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_order_return_lines_return ON commerce_order_return_lines(return_id);
CREATE INDEX idx_commerce_order_return_lines_order_line ON commerce_order_return_lines(order_line_id);

ALTER TABLE commerce_inventory_reservations
    ADD COLUMN IF NOT EXISTS scan_session_id UUID REFERENCES commerce_scan_sessions(id) ON DELETE SET NULL;
