ALTER TABLE pos_sale_lines
    ADD COLUMN IF NOT EXISTS inventory_batch_id UUID REFERENCES inventory_batches(id),
    ADD COLUMN IF NOT EXISTS warehouse_id UUID REFERENCES warehouses(id),
    ADD COLUMN IF NOT EXISTS allocation_mode VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_pos_sale_lines_allocation_mode'
    ) THEN
        ALTER TABLE pos_sale_lines
            ADD CONSTRAINT chk_pos_sale_lines_allocation_mode CHECK (
                allocation_mode IS NULL
                OR allocation_mode IN ('WAREHOUSE_POOL', 'BATCH_AUTO', 'BATCH_MANUAL')
            );
    END IF;
END $$;
