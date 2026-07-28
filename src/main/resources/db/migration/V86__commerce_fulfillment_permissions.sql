-- Epic 4.1: Fulfillment permissions

INSERT INTO permissions (id, code, name, module) VALUES
    (gen_random_uuid(), 'COMMERCE_FULFILLMENT_VIEW', 'View fulfillment queues', 'COMMERCE'),
    (gen_random_uuid(), 'COMMERCE_FULFILLMENT_MANAGE', 'Manage fulfillment operations', 'COMMERCE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ORGANIZATION_ADMIN', 'COMMERCE_ADMIN', 'COMMERCE_STORE_MANAGER')
  AND p.code IN ('COMMERCE_FULFILLMENT_VIEW', 'COMMERCE_FULFILLMENT_MANAGE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
