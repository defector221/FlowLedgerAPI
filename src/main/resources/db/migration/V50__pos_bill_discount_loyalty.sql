-- Bill-level discount + loyalty redeem fields on POS sales

ALTER TABLE pos_sales
    ADD COLUMN IF NOT EXISTS bill_discount_percent NUMERIC(8, 4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bill_discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS loyalty_points_redeemed NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50);

-- Demo loyalty balance for YRV walk-in customer (if present)
DO $$
DECLARE
    v_org_id UUID;
    v_customer_id UUID;
    v_tier_id UUID;
BEGIN
    SELECT id INTO v_org_id FROM organizations WHERE name ILIKE 'YRV%' LIMIT 1;
    IF v_org_id IS NULL THEN
        RETURN;
    END IF;

    SELECT id INTO v_customer_id
    FROM customers
    WHERE organization_id = v_org_id
    ORDER BY created_at
    LIMIT 1;

    SELECT id INTO v_tier_id
    FROM retail_loyalty_tiers
    WHERE organization_id = v_org_id AND code = 'SILVER'
    LIMIT 1;

    IF v_customer_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO retail_loyalty_accounts (
        organization_id, customer_id, tier_id, points_balance, lifetime_points
    ) VALUES (
        v_org_id, v_customer_id, v_tier_id, 500, 500
    )
    ON CONFLICT (organization_id, customer_id) DO UPDATE
        SET points_balance = GREATEST(retail_loyalty_accounts.points_balance, 500),
            lifetime_points = GREATEST(retail_loyalty_accounts.lifetime_points, 500),
            tier_id = COALESCE(retail_loyalty_accounts.tier_id, EXCLUDED.tier_id);
END $$;
