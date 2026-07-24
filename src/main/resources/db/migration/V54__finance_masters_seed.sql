-- Seed default branch / dimensions / base currency for existing orgs (idempotent)

INSERT INTO branches (id, organization_id, code, name, is_default, active)
SELECT gen_random_uuid(), o.id, 'MAIN', 'Main Branch', TRUE, TRUE
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM branches b WHERE b.organization_id = o.id AND b.code = 'MAIN'
);

INSERT INTO cost_centers (id, organization_id, code, name, active)
SELECT gen_random_uuid(), o.id, 'HO', 'Head Office', TRUE
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM cost_centers c WHERE c.organization_id = o.id AND c.code = 'HO'
);

INSERT INTO departments (id, organization_id, code, name, active)
SELECT gen_random_uuid(), o.id, 'FIN', 'Finance', TRUE
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM departments d WHERE d.organization_id = o.id AND d.code = 'FIN'
);

INSERT INTO currencies (id, organization_id, code, name, symbol, is_base, active)
SELECT gen_random_uuid(), o.id, COALESCE(NULLIF(o.currency, ''), 'INR'), 'Indian Rupee', '₹', TRUE, TRUE
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM currencies c WHERE c.organization_id = o.id AND c.is_base = TRUE
);
