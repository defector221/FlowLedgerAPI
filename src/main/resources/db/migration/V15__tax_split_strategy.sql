-- Per tax-rate split strategy and CGST/SGST share of total tax.
-- PLACE_OF_SUPPLY: intra = shares, inter = full IGST
-- NO_SPLIT_IGST / NO_SPLIT_OTHER: no component split
-- CUSTOM_PERCENT: always apply shares (ignore place of supply)

ALTER TABLE tax_rates
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';

ALTER TABLE tax_rates
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE tax_rates
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE tax_rates
    DROP CONSTRAINT IF EXISTS chk_tax_rates_split_strategy;

ALTER TABLE tax_rates
    ADD CONSTRAINT chk_tax_rates_split_strategy
        CHECK (split_strategy IN ('PLACE_OF_SUPPLY', 'NO_SPLIT_IGST', 'NO_SPLIT_OTHER', 'CUSTOM_PERCENT'));

UPDATE tax_rates
SET split_strategy = CASE tax_type
        WHEN 'IGST' THEN 'NO_SPLIT_IGST'
        WHEN 'OTHER' THEN 'NO_SPLIT_OTHER'
        ELSE 'PLACE_OF_SUPPLY'
    END,
    cgst_share_percent = CASE WHEN tax_type IN ('IGST', 'OTHER') THEN 0 ELSE 50 END,
    sgst_share_percent = CASE WHEN tax_type IN ('IGST', 'OTHER') THEN 0 ELSE 50 END
WHERE TRUE;

-- Snapshot strategy on document lines so historical docs stay stable if master changes
ALTER TABLE sales_invoice_items
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';
ALTER TABLE sales_invoice_items
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
ALTER TABLE sales_invoice_items
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE purchase_invoice_items
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';
ALTER TABLE purchase_invoice_items
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
ALTER TABLE purchase_invoice_items
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE quotation_items
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';
ALTER TABLE quotation_items
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
ALTER TABLE quotation_items
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE sales_order_items
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';
ALTER TABLE sales_order_items
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
ALTER TABLE sales_order_items
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;

ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS split_strategy VARCHAR(32) NOT NULL DEFAULT 'PLACE_OF_SUPPLY';
ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS cgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS sgst_share_percent NUMERIC(7, 4) NOT NULL DEFAULT 50;
