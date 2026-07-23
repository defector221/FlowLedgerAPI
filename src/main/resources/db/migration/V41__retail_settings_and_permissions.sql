-- Retail module: org settings flag + permissions + roles

INSERT INTO permissions (id, code, name, module) VALUES
    (gen_random_uuid(), 'RETAIL_VIEW', 'View retail', 'RETAIL'),
    (gen_random_uuid(), 'RETAIL_POS', 'Operate POS', 'RETAIL'),
    (gen_random_uuid(), 'RETAIL_STORE_MANAGE', 'Manage retail stores', 'RETAIL'),
    (gen_random_uuid(), 'RETAIL_SHIFT', 'Open and close shifts', 'RETAIL'),
    (gen_random_uuid(), 'RETAIL_ADMIN', 'Administer retail', 'RETAIL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (id, code, name, description, system_role)
VALUES
    (gen_random_uuid(), 'RETAIL_CASHIER', 'Retail Cashier', 'POS and own shift', TRUE),
    (gen_random_uuid(), 'RETAIL_STORE_MANAGER', 'Retail Store Manager', 'Store ops, shifts, local reports', TRUE),
    (gen_random_uuid(), 'RETAIL_ADMIN', 'Retail Admin', 'All retail configuration', TRUE),
    (gen_random_uuid(), 'RETAIL_REGIONAL_MANAGER', 'Retail Regional Manager', 'Multi-store reports', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code LIKE 'RETAIL_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('RETAIL_VIEW', 'RETAIL_POS', 'RETAIL_SHIFT')
WHERE r.code = 'RETAIL_CASHIER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('RETAIL_VIEW', 'RETAIL_POS', 'RETAIL_SHIFT', 'RETAIL_STORE_MANAGE')
WHERE r.code = 'RETAIL_STORE_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'RETAIL_ADMIN'
  AND p.code LIKE 'RETAIL_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('RETAIL_VIEW')
WHERE r.code = 'RETAIL_REGIONAL_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE organization_settings
    ADD COLUMN IF NOT EXISTS retail_enabled BOOLEAN NOT NULL DEFAULT FALSE;
