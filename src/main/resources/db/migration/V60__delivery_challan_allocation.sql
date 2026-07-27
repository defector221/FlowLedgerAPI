ALTER TABLE delivery_challan_items
    ADD COLUMN IF NOT EXISTS sales_order_item_id UUID REFERENCES sales_order_items(id),
    ADD COLUMN IF NOT EXISTS inventory_batch_id UUID REFERENCES inventory_batches(id),
    ADD COLUMN IF NOT EXISTS warehouse_id UUID REFERENCES warehouses(id),
    ADD COLUMN IF NOT EXISTS allocation_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS stock_reservation_id UUID REFERENCES stock_reservations(id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_delivery_challan_items_allocation_mode'
    ) THEN
        ALTER TABLE delivery_challan_items
            ADD CONSTRAINT chk_delivery_challan_items_allocation_mode CHECK (
                allocation_mode IS NULL
                OR allocation_mode IN ('WAREHOUSE_POOL', 'BATCH_AUTO', 'BATCH_MANUAL')
            );
    END IF;
END $$;
