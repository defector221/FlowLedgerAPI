-- Identify tax rate behaviour by type rather than guessing from name.
-- GST: place-of-supply decides CGST+SGST vs IGST
-- IGST: always full rate as IGST (no split)
-- OTHER: single non-GST tax applied as marked (no CGST/SGST/IGST split)

ALTER TABLE tax_rates
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

ALTER TABLE tax_rates
    DROP CONSTRAINT IF EXISTS chk_tax_rates_tax_type;

ALTER TABLE tax_rates
    ADD CONSTRAINT chk_tax_rates_tax_type
        CHECK (tax_type IN ('GST', 'IGST', 'OTHER'));

COMMENT ON COLUMN tax_rates.tax_type IS 'GST=split by place of supply; IGST=full IGST; OTHER=flat tax as marked';

ALTER TABLE sales_invoice_items
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

ALTER TABLE purchase_invoice_items
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

ALTER TABLE quotation_items
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

ALTER TABLE sales_order_items
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(16) NOT NULL DEFAULT 'GST';

-- Backfill common naming patterns on existing tax masters
UPDATE tax_rates
SET tax_type = 'IGST'
WHERE tax_type = 'GST'
  AND upper(name) LIKE '%IGST%';

UPDATE tax_rates
SET tax_type = 'OTHER'
WHERE tax_type = 'GST'
  AND upper(name) NOT LIKE '%GST%'
  AND upper(name) NOT LIKE '%IGST%';
