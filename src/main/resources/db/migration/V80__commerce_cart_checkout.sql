-- Commerce Epic 3: cart, checkout, orders, reservations, payments

CREATE TABLE commerce_carts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id            UUID NOT NULL REFERENCES retail_stores(id) ON DELETE CASCADE,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    channel             VARCHAR(32) NOT NULL DEFAULT 'WEB',
    fulfillment_type    VARCHAR(32),
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18, 4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    item_count          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_commerce_carts_active_customer_store
    ON commerce_carts (customer_id, store_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_commerce_carts_customer ON commerce_carts(customer_id);
CREATE INDEX idx_commerce_carts_store ON commerce_carts(store_id);

CREATE TABLE commerce_cart_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID NOT NULL REFERENCES commerce_carts(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    variant_id          UUID,
    quantity            NUMERIC(18, 4) NOT NULL DEFAULT 1,
    line_subtotal       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_tax            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    product_snapshot    JSONB NOT NULL DEFAULT '{}',
    price_snapshot      JSONB NOT NULL DEFAULT '{}',
    tax_snapshot        JSONB NOT NULL DEFAULT '{}',
    promotion_snapshot  JSONB NOT NULL DEFAULT '{}',
    inventory_snapshot  JSONB NOT NULL DEFAULT '{}',
    image_snapshot      JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_commerce_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_commerce_cart_items_cart ON commerce_cart_items(cart_id);

CREATE TABLE commerce_inventory_reservations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id                 UUID NOT NULL REFERENCES commerce_carts(id) ON DELETE CASCADE,
    cart_item_id            UUID NOT NULL REFERENCES commerce_cart_items(id) ON DELETE CASCADE,
    stock_reservation_id    UUID REFERENCES stock_reservations(id) ON DELETE SET NULL,
    product_id              UUID NOT NULL,
    warehouse_id            UUID NOT NULL,
    quantity                NUMERIC(18, 4) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at              TIMESTAMPTZ NOT NULL,
    renewed_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_reservations_expires ON commerce_inventory_reservations(expires_at)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_commerce_reservations_cart ON commerce_inventory_reservations(cart_id);

CREATE TABLE commerce_checkout_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID NOT NULL REFERENCES commerce_carts(id),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    fulfillment_type    VARCHAR(32) NOT NULL,
    address_id          UUID REFERENCES commerce_customer_addresses(id) ON DELETE SET NULL,
    coupon_code         VARCHAR(64),
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18, 4) NOT NULL DEFAULT 0,
    shipping_total      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    pricing_snapshot    JSONB NOT NULL DEFAULT '{}',
    expires_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_checkout_sessions_customer ON commerce_checkout_sessions(customer_id);
CREATE INDEX idx_commerce_checkout_sessions_status ON commerce_checkout_sessions(status);

CREATE TABLE commerce_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(32) NOT NULL,
    checkout_session_id UUID UNIQUE REFERENCES commerce_checkout_sessions(id),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    erp_customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,
    erp_sales_order_id  UUID,
    erp_invoice_id      UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    fulfillment_type    VARCHAR(32) NOT NULL,
    address_id          UUID REFERENCES commerce_customer_addresses(id) ON DELETE SET NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18, 4) NOT NULL DEFAULT 0,
    shipping_total      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    placed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_commerce_orders_number UNIQUE (organization_id, order_number)
);

CREATE INDEX idx_commerce_orders_customer ON commerce_orders(customer_id, placed_at DESC);

CREATE TABLE commerce_order_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES commerce_orders(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    variant_id          UUID,
    quantity            NUMERIC(18, 4) NOT NULL,
    line_subtotal       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_tax            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    product_snapshot    JSONB NOT NULL DEFAULT '{}',
    price_snapshot      JSONB NOT NULL DEFAULT '{}',
    tax_snapshot        JSONB NOT NULL DEFAULT '{}',
    promotion_snapshot  JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_order_lines_order ON commerce_order_lines(order_id);

CREATE TABLE commerce_payment_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_session_id UUID NOT NULL REFERENCES commerce_checkout_sessions(id),
    provider            VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    amount              NUMERIC(18, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    gateway_order_id    VARCHAR(128),
    gateway_payment_id  VARCHAR(128),
    idempotency_key     VARCHAR(128) NOT NULL,
    raw_response        JSONB,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_commerce_payment_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_commerce_payment_sessions_checkout ON commerce_payment_sessions(checkout_session_id);

CREATE TABLE commerce_coupon_redemptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_session_id UUID NOT NULL REFERENCES commerce_checkout_sessions(id),
    order_id            UUID REFERENCES commerce_orders(id) ON DELETE SET NULL,
    coupon_code         VARCHAR(64) NOT NULL,
    discount_applied    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    redeemed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_coupon_redemptions_session ON commerce_coupon_redemptions(checkout_session_id);
