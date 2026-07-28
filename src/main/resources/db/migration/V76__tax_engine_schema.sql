-- Tax Engine foundation: categories, versioned rules, jurisdictions, mappings, settings

CREATE TABLE tax_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    country_code    VARCHAR(3) NOT NULL DEFAULT 'IN',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    system_defined  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_tax_categories_org_code UNIQUE (organization_id, code)
);

CREATE TABLE tax_jurisdictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    country         VARCHAR(3) NOT NULL,
    state           VARCHAR(100),
    code            VARCHAR(20) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tax_jurisdictions_org_code UNIQUE (organization_id, country, code),
    CONSTRAINT chk_tax_jurisdiction_type CHECK (type IN ('GST', 'VAT', 'SalesTax'))
);

CREATE TABLE tax_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    tax_category_id UUID NOT NULL REFERENCES tax_categories(id) ON DELETE CASCADE,
    tax_type        VARCHAR(20) NOT NULL DEFAULT 'GST',
    country_code    VARCHAR(3) NOT NULL DEFAULT 'IN',
    state_code      VARCHAR(10),
    cgst_rate       NUMERIC(8, 4) NOT NULL DEFAULT 0,
    sgst_rate       NUMERIC(8, 4) NOT NULL DEFAULT 0,
    igst_rate       NUMERIC(8, 4) NOT NULL DEFAULT 0,
    cess_rate       NUMERIC(8, 4) NOT NULL DEFAULT 0,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    inclusive       BOOLEAN NOT NULL DEFAULT FALSE,
    priority        INT NOT NULL DEFAULT 0,
    reverse_charge  BOOLEAN NOT NULL DEFAULT FALSE,
    zero_rated      BOOLEAN NOT NULL DEFAULT FALSE,
    exempt          BOOLEAN NOT NULL DEFAULT FALSE,
    nil_rated       BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    version_label   VARCHAR(50),
    superseded_by   UUID REFERENCES tax_rules(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    published_by    UUID
);

CREATE INDEX idx_tax_rules_org_category ON tax_rules(organization_id, tax_category_id);
CREATE INDEX idx_tax_rules_effective ON tax_rules(organization_id, tax_category_id, effective_from, effective_to);

CREATE TABLE hsn_sac_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code            VARCHAR(20) NOT NULL,
    description     VARCHAR(500),
    type            VARCHAR(10) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hsn_sac_org_code UNIQUE (organization_id, code),
    CONSTRAINT chk_hsn_sac_type CHECK (type IN ('HSN', 'SAC'))
);

CREATE TABLE product_tax_mapping (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tax_category_id     UUID NOT NULL REFERENCES tax_categories(id) ON DELETE CASCADE,
    inherit_from_category BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_tax_mapping_product UNIQUE (organization_id, product_id)
);

CREATE TABLE product_category_tax_mapping (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    tax_category_id     UUID NOT NULL REFERENCES tax_categories(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_category_tax_mapping UNIQUE (organization_id, product_category_id)
);

CREATE TABLE organization_tax_settings (
    organization_id         UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    default_tax_category_id UUID REFERENCES tax_categories(id),
    provider_code           VARCHAR(30) NOT NULL DEFAULT 'IndiaGST',
    rounding_scale          INT NOT NULL DEFAULT 2,
    rounding_mode           VARCHAR(20) NOT NULL DEFAULT 'HALF_UP',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tax_rule_audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    tax_category_id     UUID NOT NULL REFERENCES tax_categories(id) ON DELETE CASCADE,
    previous_rule_id    UUID REFERENCES tax_rules(id),
    new_rule_id         UUID NOT NULL REFERENCES tax_rules(id) ON DELETE CASCADE,
    operation           VARCHAR(30) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Line-item tax snapshots (nullable during transition)
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE purchase_invoice_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE purchase_invoice_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE purchase_invoice_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE purchase_invoice_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE quotation_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE quotation_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE quotation_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE quotation_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE delivery_challan_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE delivery_challan_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE delivery_challan_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE delivery_challan_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE purchase_return_items ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE purchase_return_items ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE purchase_return_items ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE purchase_return_items ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

ALTER TABLE pos_sale_lines ADD COLUMN IF NOT EXISTS tax_category_id UUID;
ALTER TABLE pos_sale_lines ADD COLUMN IF NOT EXISTS tax_rule_id UUID;
ALTER TABLE pos_sale_lines ADD COLUMN IF NOT EXISTS tax_category_code VARCHAR(50);
ALTER TABLE pos_sale_lines ADD COLUMN IF NOT EXISTS tax_rule_version VARCHAR(50);

-- Bridge legacy tax_rates into tax engine for existing tenants
INSERT INTO tax_categories (id, organization_id, code, name, description, country_code, active, system_defined, created_at, updated_at)
SELECT gen_random_uuid(),
       tr.organization_id,
       'LEGACY_' || REPLACE(REPLACE(UPPER(tr.name), ' ', '_'), '%', 'PCT'),
       tr.name,
       'Migrated from tax_rates',
       'IN',
       tr.active,
       TRUE,
       COALESCE(tr.created_at, NOW()),
       COALESCE(tr.updated_at, NOW())
FROM tax_rates tr
WHERE NOT EXISTS (
    SELECT 1 FROM tax_categories tc
    WHERE tc.organization_id = tr.organization_id
      AND tc.code = 'LEGACY_' || REPLACE(REPLACE(UPPER(tr.name), ' ', '_'), '%', 'PCT')
);

INSERT INTO tax_rules (
    id, organization_id, tax_category_id, tax_type, country_code,
    cgst_rate, sgst_rate, igst_rate, cess_rate,
    effective_from, effective_to, inclusive, priority,
    reverse_charge, zero_rated, exempt, nil_rated, active, version_label,
    created_at, updated_at
)
SELECT gen_random_uuid(),
       tr.organization_id,
       tc.id,
       COALESCE(tr.tax_type, 'GST'),
       'IN',
       tr.cgst_rate,
       tr.sgst_rate,
       tr.igst_rate,
       tr.cess_rate,
       COALESCE(tr.created_at::date, CURRENT_DATE),
       NULL,
       FALSE,
       0,
       FALSE,
       tr.rate = 0,
       FALSE,
       FALSE,
       tr.active,
       'v1-migrated',
       COALESCE(tr.created_at, NOW()),
       COALESCE(tr.updated_at, NOW())
FROM tax_rates tr
JOIN tax_categories tc ON tc.organization_id = tr.organization_id
    AND tc.code = 'LEGACY_' || REPLACE(REPLACE(UPPER(tr.name), ' ', '_'), '%', 'PCT')
WHERE NOT EXISTS (
    SELECT 1 FROM tax_rules r
    WHERE r.organization_id = tr.organization_id
      AND r.tax_category_id = tc.id
      AND r.version_label = 'v1-migrated'
);

INSERT INTO product_tax_mapping (id, organization_id, product_id, tax_category_id, inherit_from_category, created_at, updated_at)
SELECT gen_random_uuid(),
       p.organization_id,
       p.id,
       tc.id,
       FALSE,
       NOW(),
       NOW()
FROM products p
JOIN tax_rates tr ON tr.id = p.tax_rate_id
JOIN tax_categories tc ON tc.organization_id = p.organization_id
    AND tc.code = 'LEGACY_' || REPLACE(REPLACE(UPPER(tr.name), ' ', '_'), '%', 'PCT')
WHERE p.tax_rate_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM product_tax_mapping m WHERE m.organization_id = p.organization_id AND m.product_id = p.id
);

-- Tax permissions
INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), v.code, v.name, 'TAX'
FROM (VALUES
    ('TAX_READ', 'View tax categories, rules, and calculations'),
    ('TAX_WRITE', 'Manage tax configuration'),
    ('TAX_ADMIN', 'Publish tax rule versions and admin settings')
) AS v(code, name)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('TAX_READ', 'TAX_WRITE', 'TAX_ADMIN')
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
