-- Commerce Epic 1: foundation schema, indexes, module + permissions

-- Global commerce customers (no organization_id)
CREATE TABLE commerce_customers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile                  VARCHAR(20) NOT NULL,
    email                   VARCHAR(255),
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    display_name            VARCHAR(200),
    profile_photo           VARCHAR(500),
    date_of_birth           DATE,
    gender                  VARCHAR(20),
    preferred_language      VARCHAR(10) DEFAULT 'en',
    status                  VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    marketing_consent       BOOLEAN NOT NULL DEFAULT FALSE,
    notification_consent    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_commerce_customers_mobile UNIQUE (mobile)
);

CREATE UNIQUE INDEX uq_commerce_customers_email ON commerce_customers (LOWER(email)) WHERE email IS NOT NULL;

CREATE TABLE commerce_customer_onboarding (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL UNIQUE REFERENCES commerce_customers(id) ON DELETE CASCADE,
    state           VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    state_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    history_json    JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_customer_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES commerce_customers(id) ON DELETE CASCADE,
    address_type    VARCHAR(20) NOT NULL DEFAULT 'HOME',
    house           VARCHAR(200),
    street          VARCHAR(300),
    landmark        VARCHAR(200),
    city            VARCHAR(100),
    district        VARCHAR(100),
    state           VARCHAR(100),
    country         VARCHAR(3) DEFAULT 'IN',
    pincode         VARCHAR(20),
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_customer_addresses_customer ON commerce_customer_addresses(customer_id);

CREATE TABLE commerce_customer_preferences (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id             UUID NOT NULL UNIQUE REFERENCES commerce_customers(id) ON DELETE CASCADE,
    language                VARCHAR(10) DEFAULT 'en',
    theme                   VARCHAR(20) DEFAULT 'light',
    currency                VARCHAR(3) DEFAULT 'INR',
    notification_prefs      JSONB,
    communication_prefs     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_customer_memberships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    favorite_store_id   UUID REFERENCES retail_stores(id) ON DELETE SET NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_commerce_membership_customer_org UNIQUE (customer_id, organization_id)
);

CREATE INDEX idx_commerce_memberships_org ON commerce_customer_memberships(organization_id);

-- Merchant extension profiles (org-scoped)
CREATE TABLE merchant_integration_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    merchant_type       VARCHAR(20) NOT NULL DEFAULT 'FLOWLEDGER',
    integration_type    VARCHAR(20) NOT NULL DEFAULT 'FLOWLEDGER',
    connector_type      VARCHAR(20) NOT NULL DEFAULT 'FLOWLEDGER',
    status              VARCHAR(32) NOT NULL DEFAULT 'INACTIVE',
    configuration_json  JSONB,
    health_status       VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    last_sync_at        TIMESTAMPTZ,
    last_health_check_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE TABLE merchant_capability_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    supports_marketplace        BOOLEAN NOT NULL DEFAULT FALSE,
    supports_delivery           BOOLEAN NOT NULL DEFAULT FALSE,
    supports_pickup             BOOLEAN NOT NULL DEFAULT FALSE,
    supports_click_collect      BOOLEAN NOT NULL DEFAULT FALSE,
    supports_scan_and_go        BOOLEAN NOT NULL DEFAULT FALSE,
    supports_scheduled_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    supports_scheduled_pickup   BOOLEAN NOT NULL DEFAULT FALSE,
    supports_wallet             BOOLEAN NOT NULL DEFAULT FALSE,
    supports_coupons            BOOLEAN NOT NULL DEFAULT FALSE,
    supports_loyalty            BOOLEAN NOT NULL DEFAULT FALSE,
    supports_recommendations    BOOLEAN NOT NULL DEFAULT FALSE,
    supports_reviews            BOOLEAN NOT NULL DEFAULT FALSE,
    supports_returns            BOOLEAN NOT NULL DEFAULT FALSE,
    supports_gift_cards         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID
);

CREATE TABLE merchant_onboarding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    state               VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    state_changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    history_json        JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

-- Store commerce profile (1:1 retail store)
CREATE TABLE store_commerce_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id                    UUID NOT NULL UNIQUE REFERENCES retail_stores(id) ON DELETE CASCADE,
    commerce_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    accept_online_orders        BOOLEAN NOT NULL DEFAULT FALSE,
    supports_delivery           BOOLEAN NOT NULL DEFAULT FALSE,
    supports_pickup             BOOLEAN NOT NULL DEFAULT FALSE,
    supports_click_collect      BOOLEAN NOT NULL DEFAULT FALSE,
    supports_scan_and_go        BOOLEAN NOT NULL DEFAULT FALSE,
    published_to_marketplace    BOOLEAN NOT NULL DEFAULT FALSE,
    publish_products            BOOLEAN NOT NULL DEFAULT FALSE,
    publish_inventory           BOOLEAN NOT NULL DEFAULT FALSE,
    publish_prices              BOOLEAN NOT NULL DEFAULT FALSE,
    visibility                  VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    discovery_radius            NUMERIC(8, 2),
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID
);

CREATE INDEX idx_store_commerce_profiles_org ON store_commerce_profiles(organization_id);

-- Marketplace read models
CREATE TABLE marketplace_store_index (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id            UUID NOT NULL UNIQUE REFERENCES retail_stores(id) ON DELETE CASCADE,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    visibility          VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    payload             JSONB NOT NULL DEFAULT '{}',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_store_published ON marketplace_store_index(published) WHERE published = TRUE;

CREATE TABLE marketplace_product_index (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id            UUID NOT NULL REFERENCES retail_stores(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    payload             JSONB NOT NULL DEFAULT '{}',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_product_store_product UNIQUE (store_id, product_id)
);

CREATE INDEX idx_marketplace_product_published ON marketplace_product_index(published) WHERE published = TRUE;
CREATE INDEX idx_marketplace_product_store ON marketplace_product_index(store_id);

CREATE TABLE commerce_config_audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(80) NOT NULL,
    entity_id           UUID NOT NULL,
    operation           VARCHAR(40) NOT NULL,
    before_json         JSONB,
    after_json          JSONB,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_config_audit_org ON commerce_config_audit_logs(organization_id, created_at DESC);

-- Commerce OTP (dev stub storage)
CREATE TABLE commerce_otp_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile          VARCHAR(20) NOT NULL,
    otp_hash        VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commerce_otp_mobile ON commerce_otp_challenges(mobile, created_at DESC);

-- Tenant permissions + roles
INSERT INTO permissions (id, code, name, module) VALUES
    (gen_random_uuid(), 'COMMERCE_VIEW', 'View commerce', 'COMMERCE'),
    (gen_random_uuid(), 'COMMERCE_ADMIN', 'Administer commerce', 'COMMERCE'),
    (gen_random_uuid(), 'COMMERCE_CONFIG_WRITE', 'Configure commerce', 'COMMERCE'),
    (gen_random_uuid(), 'COMMERCE_STORE_MANAGE', 'Manage store commerce', 'COMMERCE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (id, code, name, description, system_role) VALUES
    (gen_random_uuid(), 'COMMERCE_ADMIN', 'Commerce Admin', 'All commerce configuration', TRUE),
    (gen_random_uuid(), 'COMMERCE_STORE_MANAGER', 'Commerce Store Manager', 'Store commerce profiles', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN' AND p.code LIKE 'COMMERCE_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'COMMERCE_ADMIN' AND p.code LIKE 'COMMERCE_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('COMMERCE_VIEW', 'COMMERCE_STORE_MANAGE')
WHERE r.code = 'COMMERCE_STORE_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Platform module catalog
INSERT INTO modules (code, display_name, description, icon, category, version, is_core, enabled_by_default, status) VALUES
    ('COMMERCE', 'Commerce', 'Digital commerce and marketplace', 'ShoppingBag', 'GROWTH', '1.0.0', FALSE, FALSE, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO module_dependencies (module_code, depends_on_code) VALUES
    ('COMMERCE', 'RETAIL'),
    ('COMMERCE', 'INVENTORY')
ON CONFLICT DO NOTHING;

INSERT INTO module_features (module_code, feature_code, display_name, description, enabled_by_default) VALUES
    ('COMMERCE', 'STORES', 'Store Commerce', 'Per-store commerce configuration', TRUE),
    ('COMMERCE', 'MARKETPLACE', 'Marketplace', 'Marketplace publication', TRUE),
    ('COMMERCE', 'CUSTOMERS', 'Commerce Customers', 'Consumer identity', TRUE)
ON CONFLICT (module_code, feature_code) DO NOTHING;

-- Platform ops permissions
INSERT INTO platform.permissions (id, code, name, description) VALUES
    (gen_random_uuid(), 'COMMERCE_OPS_READ', 'Read commerce ops', 'Cross-tenant commerce read'),
    (gen_random_uuid(), 'COMMERCE_OPS_WRITE', 'Manage commerce ops', 'Cross-tenant commerce write')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM platform.roles r JOIN platform.permissions p
ON p.code IN ('COMMERCE_OPS_READ', 'COMMERCE_OPS_WRITE')
WHERE r.code = 'PLATFORM_ADMIN'
ON CONFLICT DO NOTHING;
