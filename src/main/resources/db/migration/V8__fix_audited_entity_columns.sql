-- ============================================================
-- Fix AuditedEntity columns
-- ============================================================

-- Inventory batches
ALTER TABLE inventory_batches
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;

-- Inventory transactions
ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;