-- Location FKs on transactional entities

ALTER TABLE sales_invoices
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS store_id UUID REFERENCES retail_stores(id) ON DELETE SET NULL;

ALTER TABLE purchase_invoices
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS store_id UUID REFERENCES retail_stores(id) ON DELETE SET NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS store_id UUID REFERENCES retail_stores(id) ON DELETE SET NULL;

ALTER TABLE pos_sales
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS default_branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS default_store_id UUID REFERENCES retail_stores(id) ON DELETE SET NULL;

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS default_branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sales_invoices_branch ON sales_invoices(organization_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_store ON sales_invoices(organization_id, store_id);
CREATE INDEX IF NOT EXISTS idx_purchase_invoices_branch ON purchase_invoices(organization_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_inventory_tx_branch ON inventory_transactions(organization_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_inventory_tx_store ON inventory_transactions(organization_id, store_id);
CREATE INDEX IF NOT EXISTS idx_payments_branch ON payments(organization_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_pos_sales_branch ON pos_sales(organization_id, branch_id);

-- Backfill sales invoices from warehouse/store
UPDATE sales_invoices si
SET branch_id = COALESCE(w.branch_id, b.id),
    store_id = w.store_id
FROM warehouses w
LEFT JOIN branches b ON b.organization_id = w.organization_id AND b.is_default = TRUE
WHERE si.warehouse_id = w.id
  AND si.branch_id IS NULL;

UPDATE sales_invoices si
SET branch_id = b.id
FROM branches b
WHERE si.branch_id IS NULL
  AND b.organization_id = si.organization_id
  AND b.is_default = TRUE;

UPDATE purchase_invoices pi
SET branch_id = b.id
FROM branches b
WHERE pi.branch_id IS NULL
  AND b.organization_id = pi.organization_id
  AND b.is_default = TRUE;

UPDATE inventory_transactions it
SET branch_id = w.branch_id,
    store_id = w.store_id
FROM warehouses w
WHERE it.warehouse_id = w.id
  AND it.branch_id IS NULL;

UPDATE inventory_transactions it
SET branch_id = b.id
FROM branches b
WHERE it.branch_id IS NULL
  AND b.organization_id = it.organization_id
  AND b.is_default = TRUE;

UPDATE pos_sales ps
SET branch_id = rs.branch_id
FROM retail_stores rs
WHERE ps.store_id = rs.id
  AND ps.branch_id IS NULL;

UPDATE payments p
SET branch_id = b.id
FROM branches b
WHERE p.branch_id IS NULL
  AND b.organization_id = p.organization_id
  AND b.is_default = TRUE;
