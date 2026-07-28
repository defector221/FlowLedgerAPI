ALTER TABLE commerce_inventory_reservations
    ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES commerce_orders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_commerce_reservations_order
    ON commerce_inventory_reservations(order_id)
    WHERE order_id IS NOT NULL;
