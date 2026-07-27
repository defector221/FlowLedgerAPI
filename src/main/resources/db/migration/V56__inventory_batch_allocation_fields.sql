ALTER TABLE inventory_batches
    ADD COLUMN IF NOT EXISTS received_date DATE,
    ADD COLUMN IF NOT EXISTS lot_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS quality_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_batches_quality_status'
    ) THEN
        ALTER TABLE inventory_batches
            ADD CONSTRAINT chk_inventory_batches_quality_status CHECK (
                quality_status IN ('AVAILABLE', 'BLOCKED', 'DAMAGED')
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inventory_batches_allocation
    ON inventory_batches (organization_id, product_id, warehouse_id, quality_status, expiry_date);
