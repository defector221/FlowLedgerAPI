ALTER TABLE stock_reservations
    ADD COLUMN IF NOT EXISTS inventory_batch_id UUID REFERENCES inventory_batches(id),
    ADD COLUMN IF NOT EXISTS allocation_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS line_reference_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_reservations_allocation_mode'
    ) THEN
        ALTER TABLE stock_reservations
            ADD CONSTRAINT chk_stock_reservations_allocation_mode CHECK (
                allocation_mode IS NULL
                OR allocation_mode IN ('WAREHOUSE_POOL', 'BATCH_AUTO', 'BATCH_MANUAL')
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_reservations_batch
    ON stock_reservations (organization_id, inventory_batch_id, status)
    WHERE inventory_batch_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_reservations_active_line
    ON stock_reservations (organization_id, reference_type, reference_id, line_reference_id)
    WHERE status = 'ACTIVE' AND line_reference_id IS NOT NULL;
