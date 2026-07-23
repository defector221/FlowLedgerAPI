-- Platform module catalog, editions, and per-organization entitlements (Phase 1)

CREATE TABLE IF NOT EXISTS modules (
    code                VARCHAR(64) PRIMARY KEY,
    display_name        VARCHAR(128) NOT NULL,
    description         TEXT,
    icon                VARCHAR(64),
    category            VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    version             VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    is_core             BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_by_default  BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT modules_status_chk CHECK (status IN ('ACTIVE', 'DEPRECATED', 'COMING_SOON'))
);

CREATE TABLE IF NOT EXISTS module_dependencies (
    module_code     VARCHAR(64) NOT NULL REFERENCES modules(code) ON DELETE CASCADE,
    depends_on_code VARCHAR(64) NOT NULL REFERENCES modules(code) ON DELETE CASCADE,
    PRIMARY KEY (module_code, depends_on_code),
    CONSTRAINT module_dependencies_no_self CHECK (module_code <> depends_on_code)
);

CREATE TABLE IF NOT EXISTS module_features (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_code         VARCHAR(64) NOT NULL REFERENCES modules(code) ON DELETE CASCADE,
    feature_code        VARCHAR(64) NOT NULL,
    display_name        VARCHAR(128) NOT NULL,
    description         TEXT,
    enabled_by_default  BOOLEAN NOT NULL DEFAULT TRUE,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT module_features_status_chk CHECK (status IN ('ACTIVE', 'DEPRECATED', 'COMING_SOON')),
    CONSTRAINT module_features_unique UNIQUE (module_code, feature_code)
);

CREATE TABLE IF NOT EXISTS feature_dependencies (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_code             VARCHAR(64) NOT NULL,
    feature_code            VARCHAR(64) NOT NULL,
    depends_on_module_code  VARCHAR(64) NOT NULL,
    depends_on_feature_code VARCHAR(64),
    CONSTRAINT feature_dependencies_fk
        FOREIGN KEY (module_code, feature_code)
        REFERENCES module_features(module_code, feature_code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS editions (
    code            VARCHAR(32) PRIMARY KEY,
    display_name    VARCHAR(128) NOT NULL,
    description     TEXT,
    rank            INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS edition_modules (
    edition_code    VARCHAR(32) NOT NULL REFERENCES editions(code) ON DELETE CASCADE,
    module_code     VARCHAR(64) NOT NULL REFERENCES modules(code) ON DELETE CASCADE,
    PRIMARY KEY (edition_code, module_code)
);

CREATE TABLE IF NOT EXISTS organization_modules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    module_code     VARCHAR(64) NOT NULL REFERENCES modules(code),
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    licensed        BOOLEAN NOT NULL DEFAULT TRUE,
    trial           BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ,
    configuration   JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT organization_modules_unique UNIQUE (organization_id, module_code)
);

CREATE INDEX IF NOT EXISTS idx_organization_modules_org ON organization_modules(organization_id);

CREATE TABLE IF NOT EXISTS organization_features (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    module_code     VARCHAR(64) NOT NULL,
    feature_code    VARCHAR(64) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    licensed        BOOLEAN NOT NULL DEFAULT TRUE,
    trial           BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ,
    configuration   JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT organization_features_unique UNIQUE (organization_id, module_code, feature_code),
    CONSTRAINT organization_features_fk
        FOREIGN KEY (module_code, feature_code)
        REFERENCES module_features(module_code, feature_code)
);

CREATE INDEX IF NOT EXISTS idx_organization_features_org ON organization_features(organization_id);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS edition_code VARCHAR(32);

-- Catalog seed
INSERT INTO modules (code, display_name, description, icon, category, version, is_core, enabled_by_default, status) VALUES
    ('DASHBOARD',   'Dashboard',   'Home dashboard',              'LayoutDashboard', 'CORE',     '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('SALES',       'Sales',       'Quotations, orders, invoices','ReceiptText',     'CORE',     '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('PURCHASE',    'Purchasing',  'POs, GRN, purchase invoices', 'ShoppingCart',    'CORE',     '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('INVENTORY',   'Inventory',   'Stock, warehouses, products', 'Boxes',           'CORE',     '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('PAYMENTS',    'Payments',    'Customer and supplier payments','Wallet',        'CORE',     '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('ACCOUNTING',  'Accounting',  'COA, journals, ledgers',      'BookOpen',        'FINANCE',  '1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('CRM',         'CRM',         'Leads, campaigns, marketing', 'Target',          'GROWTH',   '1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('REPORTS',     'Reports',     'Business and finance reports','BarChart3',       'ANALYTICS','1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('TRANSPORT',   'Transport',   'Fleet, shipments, tracking',  'Truck',           'OPS',      '1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('RETAIL',      'Retail',      'POS, stores, loyalty',        'Store',           'OPS',      '1.0.0', FALSE, FALSE, 'ACTIVE'),
    ('AI',          'AI',          'Assistant and automation',    'Sparkles',        'AI',       '1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('SETTINGS',    'Settings',    'Organization administration', 'Settings',        'ADMIN',    '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('AUDIT',       'Audit',       'Audit logs',                  'ShieldCheck',     'ADMIN',    '1.0.0', TRUE,  TRUE,  'ACTIVE'),
    ('WAREHOUSE',   'Warehouse',   'Advanced warehouse ops',      'Building2',       'OPS',      '1.0.0', FALSE, TRUE,  'ACTIVE'),
    ('MANUFACTURING','Manufacturing','MRP and production',        'Workflow',        'OPS',      '1.0.0', FALSE, FALSE, 'COMING_SOON'),
    ('HR',          'HR',          'Human resources',             'Users',           'PEOPLE',   '1.0.0', FALSE, FALSE, 'COMING_SOON')
ON CONFLICT (code) DO NOTHING;

INSERT INTO module_dependencies (module_code, depends_on_code) VALUES
    ('RETAIL', 'INVENTORY'),
    ('RETAIL', 'ACCOUNTING'),
    ('TRANSPORT', 'INVENTORY'),
    ('WAREHOUSE', 'INVENTORY'),
    ('MANUFACTURING', 'INVENTORY'),
    ('MANUFACTURING', 'PURCHASE')
ON CONFLICT DO NOTHING;

INSERT INTO module_features (module_code, feature_code, display_name, description, enabled_by_default) VALUES
    ('RETAIL', 'POS', 'POS', 'Point of sale checkout', TRUE),
    ('RETAIL', 'STORES', 'Stores', 'Store master', TRUE),
    ('RETAIL', 'CATALOG', 'Catalog', 'Retail catalog and variants', TRUE),
    ('RETAIL', 'RETURNS', 'Returns', 'Retail returns', TRUE),
    ('RETAIL', 'PRICING', 'Pricing', 'Promotions and price lists', TRUE),
    ('RETAIL', 'GIFT_CARDS', 'Gift Cards', 'Gift card management', TRUE),
    ('RETAIL', 'LOYALTY', 'Loyalty', 'Loyalty programs', TRUE),
    ('RETAIL', 'BARCODE', 'Barcode', 'Barcode / labels', TRUE),
    ('RETAIL', 'INVENTORY_SYNC', 'Inventory Sync', 'Location inventory sync', TRUE),
    ('TRANSPORT', 'VEHICLES', 'Vehicles', 'Fleet vehicles', TRUE),
    ('TRANSPORT', 'DRIVERS', 'Drivers', 'Driver master', TRUE),
    ('TRANSPORT', 'TRACKING', 'Shipment Tracking', 'Track shipments', TRUE),
    ('TRANSPORT', 'GPS', 'GPS', 'GPS integrations', FALSE),
    ('TRANSPORT', 'ROUTE_OPTIMIZATION', 'Route Optimization', 'Route planning', FALSE),
    ('TRANSPORT', 'DELIVERY_PLANNING', 'Delivery Planning', 'Delivery planning', TRUE),
    ('TRANSPORT', 'POD', 'Proof Of Delivery', 'Proof of delivery', TRUE),
    ('AI', 'ASSISTANT', 'Assistant', 'AI chat assistant', TRUE),
    ('AI', 'INSIGHTS', 'Insights', 'Recommendations', TRUE),
    ('AI', 'FORECASTS', 'Forecasts', 'Analytics forecasts', TRUE),
    ('AI', 'AUTOMATION', 'Automation', 'AI workflows', TRUE)
ON CONFLICT (module_code, feature_code) DO NOTHING;

INSERT INTO editions (code, display_name, description, rank) VALUES
    ('LITE', 'Lite', 'Core ERP', 10),
    ('STANDARD', 'Standard', 'Lite plus accounting, CRM, reports', 20),
    ('PROFESSIONAL', 'Professional', 'Standard plus retail, transport, warehouse', 30),
    ('ENTERPRISE', 'Enterprise', 'All active modules', 40),
    ('CUSTOM', 'Custom', 'Individually selected modules', 50)
ON CONFLICT (code) DO NOTHING;

-- LITE
INSERT INTO edition_modules (edition_code, module_code)
SELECT 'LITE', code FROM modules
WHERE code IN ('DASHBOARD', 'SALES', 'PURCHASE', 'INVENTORY', 'PAYMENTS', 'SETTINGS', 'AUDIT')
ON CONFLICT DO NOTHING;

-- STANDARD
INSERT INTO edition_modules (edition_code, module_code)
SELECT 'STANDARD', code FROM modules
WHERE code IN (
    'DASHBOARD', 'SALES', 'PURCHASE', 'INVENTORY', 'PAYMENTS', 'SETTINGS', 'AUDIT',
    'ACCOUNTING', 'CRM', 'REPORTS', 'WAREHOUSE'
)
ON CONFLICT DO NOTHING;

-- PROFESSIONAL
INSERT INTO edition_modules (edition_code, module_code)
SELECT 'PROFESSIONAL', code FROM modules
WHERE code IN (
    'DASHBOARD', 'SALES', 'PURCHASE', 'INVENTORY', 'PAYMENTS', 'SETTINGS', 'AUDIT',
    'ACCOUNTING', 'CRM', 'REPORTS', 'WAREHOUSE', 'TRANSPORT', 'RETAIL', 'AI'
)
ON CONFLICT DO NOTHING;

-- ENTERPRISE = all ACTIVE modules
INSERT INTO edition_modules (edition_code, module_code)
SELECT 'ENTERPRISE', code FROM modules WHERE status = 'ACTIVE'
ON CONFLICT DO NOTHING;

-- Existing orgs: PROFESSIONAL by default (preserves current product surface)
UPDATE organizations SET edition_code = 'PROFESSIONAL' WHERE edition_code IS NULL;

ALTER TABLE organizations
    ALTER COLUMN edition_code SET DEFAULT 'PROFESSIONAL';

ALTER TABLE organizations
    ALTER COLUMN edition_code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'organizations_edition_fk'
    ) THEN
        ALTER TABLE organizations
            ADD CONSTRAINT organizations_edition_fk
            FOREIGN KEY (edition_code) REFERENCES editions(code);
    END IF;
END $$;

-- Backfill org modules from edition + retail/transport mirrors
INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
SELECT o.id, em.module_code, TRUE, TRUE, FALSE, '{}'::jsonb
FROM organizations o
JOIN edition_modules em ON em.edition_code = o.edition_code
ON CONFLICT (organization_id, module_code) DO NOTHING;

-- Retail mirrors settings flag (do not enable if flag false)
UPDATE organization_modules om
SET enabled = COALESCE(os.retail_enabled, FALSE),
    updated_at = NOW()
FROM organization_settings os
WHERE om.organization_id = os.organization_id
  AND om.module_code = 'RETAIL';

INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
SELECT os.organization_id, 'RETAIL', os.retail_enabled, TRUE, FALSE, '{}'::jsonb
FROM organization_settings os
WHERE NOT EXISTS (
    SELECT 1 FROM organization_modules om
    WHERE om.organization_id = os.organization_id AND om.module_code = 'RETAIL'
);

-- Transport: preserve prior UX (permission-gated; treat as enabled for existing tenants)
UPDATE organization_modules om
SET enabled = TRUE,
    updated_at = NOW()
WHERE om.module_code = 'TRANSPORT';

INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
SELECT o.id, 'TRANSPORT', TRUE, TRUE, FALSE, '{}'::jsonb
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_modules om
    WHERE om.organization_id = o.id AND om.module_code = 'TRANSPORT'
);

-- Sync mirror columns from entitlements for consistency
UPDATE organization_settings os
SET retail_enabled = om.enabled,
    updated_at = NOW()
FROM organization_modules om
WHERE os.organization_id = om.organization_id AND om.module_code = 'RETAIL';

UPDATE organization_settings os
SET transport_enabled = om.enabled,
    updated_at = NOW()
FROM organization_modules om
WHERE os.organization_id = om.organization_id AND om.module_code = 'TRANSPORT';

-- Default feature entitlements for enabled modules (catalog defaults)
INSERT INTO organization_features (organization_id, module_code, feature_code, enabled, licensed, trial, configuration)
SELECT om.organization_id, mf.module_code, mf.feature_code, mf.enabled_by_default, TRUE, FALSE, '{}'::jsonb
FROM organization_modules om
JOIN module_features mf ON mf.module_code = om.module_code
WHERE om.enabled = TRUE
ON CONFLICT (organization_id, module_code, feature_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS plan_edition_defaults (
    plan_code       VARCHAR(32) PRIMARY KEY,
    edition_code    VARCHAR(32) NOT NULL REFERENCES editions(code)
);

INSERT INTO plan_edition_defaults (plan_code, edition_code) VALUES
    ('FREE', 'PROFESSIONAL'),
    ('STARTER', 'PROFESSIONAL'),
    ('BUSINESS', 'ENTERPRISE'),
    ('PRO', 'ENTERPRISE')
ON CONFLICT (plan_code) DO NOTHING;
