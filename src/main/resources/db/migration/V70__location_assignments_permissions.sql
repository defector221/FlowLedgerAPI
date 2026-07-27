-- User location assignments and hierarchy permissions

CREATE TABLE IF NOT EXISTS user_location_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    scope_type      VARCHAR(20) NOT NULL,
    branch_id       UUID REFERENCES branches(id) ON DELETE CASCADE,
    store_id        UUID REFERENCES retail_stores(id) ON DELETE CASCADE,
    warehouse_id    UUID REFERENCES warehouses(id) ON DELETE CASCADE,
    terminal_id     UUID REFERENCES retail_terminals(id) ON DELETE CASCADE,
    role_code       VARCHAR(50),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ula_scope_type CHECK (scope_type IN (
        'ORGANIZATION', 'BRANCH', 'STORE', 'WAREHOUSE', 'TERMINAL'
    ))
);

CREATE INDEX idx_ula_org_user ON user_location_assignments(organization_id, user_id) WHERE active = TRUE;
CREATE INDEX idx_ula_branch ON user_location_assignments(branch_id) WHERE active = TRUE AND branch_id IS NOT NULL;
CREATE INDEX idx_ula_store ON user_location_assignments(store_id) WHERE active = TRUE AND store_id IS NOT NULL;

-- Permissions
INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), v.code, v.name, v.module
FROM (VALUES
    ('BRANCH_READ', 'View branches', 'SETTINGS'),
    ('STORE_READ', 'View stores', 'SETTINGS'),
    ('STORE_MANAGE', 'Manage stores', 'SETTINGS'),
    ('WAREHOUSE_READ', 'View warehouses', 'INVENTORY'),
    ('WAREHOUSE_MANAGE', 'Manage warehouses', 'INVENTORY'),
    ('TERMINAL_MANAGE', 'Manage POS terminals', 'RETAIL'),
    ('DRAWER_MANAGE', 'Manage cash drawers', 'RETAIL')
) AS v(code, name, module)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- Roles
INSERT INTO roles (id, code, name, description)
SELECT gen_random_uuid(), v.code, v.name, v.description
FROM (VALUES
    ('BRANCH_MANAGER', 'Branch Manager', 'Manage branch operations and reports'),
    ('WAREHOUSE_MANAGER', 'Warehouse Manager', 'Manage warehouse inventory and transfers'),
    ('REGIONAL_MANAGER', 'Regional Manager', 'Manage multiple branches in a region')
) AS v(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.code = v.code);

-- BRANCH_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'BRANCH_MANAGER'
  AND p.code IN ('BRANCH_READ', 'BRANCH_MANAGE', 'STORE_READ', 'WAREHOUSE_READ', 'VOUCHER_READ')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- WAREHOUSE_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'WAREHOUSE_MANAGER'
  AND p.code IN ('WAREHOUSE_READ', 'WAREHOUSE_MANAGE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- REGIONAL_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'REGIONAL_MANAGER'
  AND p.code IN (
      'BRANCH_READ', 'BRANCH_MANAGE', 'STORE_READ', 'STORE_MANAGE',
      'WAREHOUSE_READ', 'RETAIL_VIEW', 'VOUCHER_READ'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ORGANIZATION_ADMIN gets new permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code IN (
      'BRANCH_READ', 'STORE_READ', 'STORE_MANAGE',
      'WAREHOUSE_READ', 'WAREHOUSE_MANAGE', 'TERMINAL_MANAGE', 'DRAWER_MANAGE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Store-aware document sequences
ALTER TABLE document_sequences
    ADD COLUMN IF NOT EXISTS store_id UUID;

DROP INDEX IF EXISTS uq_document_sequences_scope;
CREATE UNIQUE INDEX uq_document_sequences_scope
    ON document_sequences (
        organization_id,
        document_type,
        financial_year,
        COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'),
        COALESCE(store_id, '00000000-0000-0000-0000-000000000000')
    );
