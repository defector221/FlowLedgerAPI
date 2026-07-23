-- Returns, loyalty, gift cards

CREATE TABLE pos_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES retail_stores(id),
    original_pos_sale_id UUID REFERENCES pos_sales(id),
    original_invoice_id UUID,
    sales_return_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    reason VARCHAR(40) NOT NULL,
    refund_mode VARCHAR(40) NOT NULL DEFAULT 'REFUND',
    notes TEXT,
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE pos_return_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    pos_return_id UUID NOT NULL REFERENCES pos_returns(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL,
    rate NUMERIC(18, 4) NOT NULL,
    line_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE retail_store_credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_store_credit_customer UNIQUE (organization_id, customer_id)
);

CREATE TABLE retail_loyalty_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    min_points NUMERIC(18, 2) NOT NULL DEFAULT 0,
    earn_rate NUMERIC(8, 4) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_loyalty_tiers_org_code UNIQUE (organization_id, code)
);

CREATE TABLE retail_loyalty_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    tier_id UUID REFERENCES retail_loyalty_tiers(id),
    points_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
    lifetime_points NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_loyalty_customer UNIQUE (organization_id, customer_id)
);

CREATE TABLE retail_loyalty_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES retail_loyalty_accounts(id) ON DELETE CASCADE,
    txn_type VARCHAR(30) NOT NULL,
    points NUMERIC(18, 2) NOT NULL,
    reference_type VARCHAR(40),
    reference_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE TABLE retail_gift_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    card_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    initial_balance NUMERIC(18, 2) NOT NULL,
    balance NUMERIC(18, 2) NOT NULL,
    customer_id UUID,
    expires_at DATE,
    activated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_gift_cards_number UNIQUE (organization_id, card_number)
);

CREATE TABLE retail_customer_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    membership_code VARCHAR(50),
    birthday DATE,
    anniversary DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_customer_profile UNIQUE (organization_id, customer_id)
);
