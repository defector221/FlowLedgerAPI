-- Transport permissions and organization-level settings

INSERT INTO permissions (id, code, name, module) VALUES
    (gen_random_uuid(), 'TRANSPORT_VIEW', 'View transport', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_CREATE', 'Create transport records', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_EDIT', 'Edit transport records', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_ASSIGN', 'Assign vehicles and drivers', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_DISPATCH', 'Dispatch shipments', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_TRACK', 'Track shipments', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_CLOSE', 'Close shipments', 'TRANSPORT'),
    (gen_random_uuid(), 'TRANSPORT_ADMIN', 'Administer transport', 'TRANSPORT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code LIKE 'TRANSPORT_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TRANSPORT_VIEW'
WHERE r.code IN ('ACCOUNTANT', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE organization_settings
    ADD COLUMN IF NOT EXISTS transport_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS transport_required_default BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS transport_allow_override BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS transport_approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS transport_default_freight_payer VARCHAR(32),
    ADD COLUMN IF NOT EXISTS transport_delay_threshold_hours INT DEFAULT 24;
