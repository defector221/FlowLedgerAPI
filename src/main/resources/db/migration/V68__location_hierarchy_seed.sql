-- Idempotent backfill: head office branch, warehouse types, store linkage, default drawers

-- Ensure MAIN branch is head office
UPDATE branches
SET is_head_office = TRUE,
    is_default = TRUE
WHERE code = 'MAIN'
  AND (is_head_office = FALSE OR is_default = FALSE);

-- Default org warehouse → CENTRAL
UPDATE warehouses w
SET warehouse_type = 'CENTRAL'
WHERE w.is_default = TRUE
  AND w.warehouse_type = 'CENTRAL'
  AND NOT EXISTS (
      SELECT 1 FROM retail_stores rs
      WHERE rs.warehouse_id = w.id AND rs.deleted = FALSE
  );

-- Link existing stores to MAIN branch
UPDATE retail_stores rs
SET branch_id = b.id
FROM branches b
WHERE rs.branch_id IS NULL
  AND b.organization_id = rs.organization_id
  AND b.code = 'MAIN';

-- Warehouses used by stores → STORE type
UPDATE warehouses w
SET warehouse_type = 'STORE',
    store_id = rs.id,
    branch_id = rs.branch_id
FROM retail_stores rs
WHERE rs.warehouse_id = w.id
  AND rs.deleted = FALSE
  AND (w.store_id IS NULL OR w.warehouse_type <> 'STORE');

-- Remaining non-store warehouses → BRANCH type under MAIN
UPDATE warehouses w
SET warehouse_type = 'BRANCH',
    branch_id = b.id
FROM branches b
WHERE w.organization_id = b.organization_id
  AND b.code = 'MAIN'
  AND w.store_id IS NULL
  AND w.warehouse_type = 'CENTRAL'
  AND w.is_default = FALSE;

-- Create default store for orgs without any retail store
INSERT INTO retail_stores (
    id, organization_id, branch_id, code, name, warehouse_id,
    store_type, status, country
)
SELECT
    gen_random_uuid(),
    o.id,
    b.id,
    'DEFAULT',
    'Default Store',
    w.id,
    'RETAIL',
    'ACTIVE',
    COALESCE(o.country, 'IN')
FROM organizations o
JOIN branches b ON b.organization_id = o.id AND b.code = 'MAIN'
JOIN warehouses w ON w.organization_id = o.id AND w.is_default = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM retail_stores rs
    WHERE rs.organization_id = o.id AND rs.deleted = FALSE
);

-- Mark default warehouse as STORE when only default store uses it
UPDATE warehouses w
SET warehouse_type = 'STORE',
    store_id = rs.id,
    branch_id = rs.branch_id
FROM retail_stores rs
WHERE rs.organization_id = w.organization_id
  AND rs.code = 'DEFAULT'
  AND rs.warehouse_id = w.id
  AND rs.deleted = FALSE
  AND w.store_id IS NULL;

-- Backfill branch_id on any remaining stores without branch
UPDATE retail_stores rs
SET branch_id = b.id
FROM branches b
WHERE rs.branch_id IS NULL
  AND b.organization_id = rs.organization_id
  AND b.is_default = TRUE;

-- Default cash drawer per active terminal
INSERT INTO cash_drawers (id, organization_id, terminal_id, drawer_code, drawer_name, status)
SELECT
    gen_random_uuid(),
    t.organization_id,
    t.id,
    'DRAWER-1',
    'Main Drawer',
    'ACTIVE'
FROM retail_terminals t
WHERE t.deleted = FALSE
  AND t.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM cash_drawers cd
      WHERE cd.terminal_id = t.id AND cd.deleted = FALSE
  );

-- Link open shifts to default drawer where missing
UPDATE retail_shifts s
SET drawer_id = cd.id
FROM cash_drawers cd
WHERE s.drawer_id IS NULL
  AND cd.terminal_id = s.terminal_id
  AND cd.deleted = FALSE
  AND cd.drawer_code = 'DRAWER-1';
