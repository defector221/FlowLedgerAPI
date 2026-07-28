-- Epic 5B: Customer Engagement (wallet, referral, notifications, lists, reviews)

CREATE TABLE commerce_wallet_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    account_type        VARCHAR(32) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance             NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallet_account UNIQUE (customer_id, organization_id, account_type, currency)
);

CREATE TABLE commerce_wallet_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID NOT NULL REFERENCES commerce_wallet_accounts(id),
    direction           VARCHAR(8) NOT NULL,
    amount              NUMERIC(18, 2) NOT NULL,
    balance_after       NUMERIC(18, 2) NOT NULL,
    reference_type      VARCHAR(64) NOT NULL,
    reference_id        UUID,
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_entries_account ON commerce_wallet_entries(account_id, created_at);

CREATE TABLE commerce_referral_codes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    code                VARCHAR(32) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_referral_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    referrer_customer_id UUID NOT NULL REFERENCES commerce_customers(id),
    referee_customer_id UUID NOT NULL REFERENCES commerce_customers(id),
    order_id            UUID REFERENCES commerce_orders(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rewarded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_customer_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id   UUID REFERENCES organizations(id),
    event_type          VARCHAR(128) NOT NULL,
    title               VARCHAR(256) NOT NULL,
    body                TEXT,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_notifications_customer ON commerce_customer_notifications(customer_id, created_at DESC);

CREATE TABLE commerce_wishlist_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    product_id          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wishlist_item UNIQUE (customer_id, store_id, product_id)
);

CREATE TABLE commerce_saved_lists (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    name                VARCHAR(128) NOT NULL,
    share_token         VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_saved_list_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id             UUID NOT NULL REFERENCES commerce_saved_lists(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    quantity            NUMERIC(18, 4) NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_reviews (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    review_type         VARCHAR(32) NOT NULL,
    store_id            UUID REFERENCES retail_stores(id),
    product_id          UUID,
    order_id            UUID REFERENCES commerce_orders(id),
    rating              INT NOT NULL,
    comment             TEXT,
    moderated           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_commerce_reviews_target ON commerce_reviews(review_type, store_id, product_id);
