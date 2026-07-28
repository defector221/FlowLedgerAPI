-- Grant commerce tenant permissions to retail roles that manage commerce in FlowLedgerUI.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('COMMERCE_VIEW', 'COMMERCE_CONFIG_WRITE', 'COMMERCE_STORE_MANAGE')
WHERE r.code IN ('RETAIL_ADMIN', 'RETAIL_STORE_MANAGER', 'ORGANIZATION_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- COMMERCE_ADMIN role should already have all COMMERCE_* permissions; ensure store managers can view.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'COMMERCE_VIEW'
WHERE r.code = 'COMMERCE_STORE_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
