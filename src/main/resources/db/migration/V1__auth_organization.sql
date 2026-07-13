-- FlowLedger core schema: auth, organizations, RBAC
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE organizations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    legal_name          VARCHAR(255),
    logo_object_key     VARCHAR(500),
    gstin               VARCHAR(20),
    pan                 VARCHAR(20),
    cin                 VARCHAR(30),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    website             VARCHAR(255),
    billing_address     TEXT,
    shipping_address    TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    state_code          VARCHAR(10),
    country             VARCHAR(100) NOT NULL DEFAULT 'India',
    currency            VARCHAR(10) NOT NULL DEFAULT 'INR',
    financial_year_start VARCHAR(10) NOT NULL DEFAULT '04-01',
    invoice_prefix      VARCHAR(50) NOT NULL DEFAULT 'INV',
    purchase_invoice_prefix VARCHAR(50) NOT NULL DEFAULT 'PINV',
    quotation_prefix    VARCHAR(50) NOT NULL DEFAULT 'QT',
    sales_order_prefix  VARCHAR(50) NOT NULL DEFAULT 'SO',
    delivery_challan_prefix VARCHAR(50) NOT NULL DEFAULT 'DC',
    purchase_order_prefix VARCHAR(50) NOT NULL DEFAULT 'PO',
    payment_prefix      VARCHAR(50) NOT NULL DEFAULT 'PAY',
    invoice_number_format VARCHAR(100) NOT NULL DEFAULT '{PREFIX}/{FY}/{SEQ:6}',
    default_tax_rate_id UUID,
    bank_name           VARCHAR(200),
    bank_account_number VARCHAR(50),
    bank_ifsc           VARCHAR(20),
    bank_branch         VARCHAR(200),
    upi_id              VARCHAR(100),
    payment_terms       VARCHAR(255),
    allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    module      VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID REFERENCES organizations(id),
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100),
    phone               VARCHAR(30),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    password_reset_token VARCHAR(255),
    password_reset_expiry TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_users_org_email UNIQUE (organization_id, email)
);

CREATE UNIQUE INDEX uq_users_super_admin_email ON users (email) WHERE organization_id IS NULL;

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE organization_settings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    inventory_deduction_event VARCHAR(50) NOT NULL DEFAULT 'INVOICE_CONFIRM',
    tax_inclusive_default BOOLEAN NOT NULL DEFAULT FALSE,
    round_off_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    default_warehouse_id UUID,
    settings_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE document_sequences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL,
    financial_year  VARCHAR(20) NOT NULL,
    prefix          VARCHAR(50) NOT NULL,
    next_value      BIGINT NOT NULL DEFAULT 1,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_doc_seq UNIQUE (organization_id, document_type, financial_year)
);

CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
