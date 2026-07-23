-- YRV Solutions POS / retail demo seed.
-- Requires YRV org from V22 (+ inventory products from V23 when available).
-- Idempotent: skips when store code YRV-MAIN already exists.

DO $yrv_pos$
DECLARE
    v_org_id UUID;
    v_user_id UUID;
    v_wh_id UUID;
    v_store_type_id UUID := gen_random_uuid();
    v_store_id UUID := gen_random_uuid();
    v_counter_id UUID := gen_random_uuid();
    v_terminal_id UUID := gen_random_uuid();
    v_cashier_id UUID := gen_random_uuid();
    v_shift_id UUID := gen_random_uuid();
    v_brand_id UUID := gen_random_uuid();
    v_dept_id UUID := gen_random_uuid();
    v_coll_id UUID := gen_random_uuid();
    v_price_list_id UUID := gen_random_uuid();
    v_customer_id UUID;
    v_sale_held_id UUID := gen_random_uuid();
    v_sale_done_id UUID := gen_random_uuid();
    v_product_id UUID;
    v_product_name TEXT;
    v_product_barcode TEXT;
    v_rate NUMERIC(18, 4);
    v_tax NUMERIC(18, 2);
    v_subtotal NUMERIC(18, 2);
    v_grand NUMERIC(18, 2);
    v_prod RECORD;
    v_i INT := 0;
    v_barcode TEXT;
    v_has_inv_locations BOOLEAN;
BEGIN
    SELECT o.id INTO v_org_id
    FROM organizations o
    WHERE o.name = 'YRV Solutions'
    ORDER BY o.created_at DESC
    LIMIT 1;

    IF v_org_id IS NULL THEN
        RAISE NOTICE 'V49 skipped: YRV Solutions organization not found';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM retail_stores
        WHERE organization_id = v_org_id AND code = 'YRV-MAIN' AND deleted = FALSE
    ) THEN
        RAISE NOTICE 'V49 skipped: POS seed already present for YRV (store YRV-MAIN)';
        RETURN;
    END IF;

    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.organization_id = v_org_id
       OR u.last_active_organization_id = v_org_id
    ORDER BY CASE WHEN lower(u.email) = lower('kashyap221@gmail.com') THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'V49 aborted: no user found for YRV org %', v_org_id;
    END IF;

    SELECT id INTO v_wh_id
    FROM warehouses
    WHERE organization_id = v_org_id AND is_default = TRUE
    LIMIT 1;
    IF v_wh_id IS NULL THEN
        SELECT id INTO v_wh_id
        FROM warehouses
        WHERE organization_id = v_org_id
        LIMIT 1;
    END IF;
    IF v_wh_id IS NULL THEN
        RAISE EXCEPTION 'V49 aborted: no warehouse for YRV org %', v_org_id;
    END IF;

    -- Enable retail for settings + platform module entitlements
    UPDATE organization_settings
    SET retail_enabled = TRUE,
        updated_at = NOW()
    WHERE organization_id = v_org_id;

    INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
    VALUES (v_org_id, 'RETAIL', TRUE, TRUE, FALSE, '{}'::jsonb)
    ON CONFLICT (organization_id, module_code) DO UPDATE
        SET enabled = TRUE,
            licensed = TRUE,
            updated_at = NOW();

    INSERT INTO organization_features (
        organization_id, module_code, feature_code, enabled, licensed, trial, configuration
    )
    SELECT v_org_id, mf.module_code, mf.feature_code, TRUE, TRUE, FALSE, '{}'::jsonb
    FROM module_features mf
    WHERE mf.module_code = 'RETAIL'
    ON CONFLICT (organization_id, module_code, feature_code) DO UPDATE
        SET enabled = TRUE,
            licensed = TRUE,
            updated_at = NOW();

    -- Store type + flagship store
    INSERT INTO retail_store_types (
        id, organization_id, code, name, created_by, updated_by
    ) VALUES (
        v_store_type_id, v_org_id, 'FLAGSHIP', 'Flagship Store', v_user_id, v_user_id
    );

    INSERT INTO retail_stores (
        id, organization_id, code, name, store_type_id, warehouse_id,
        address, city, state, phone, status, created_by, updated_by
    ) VALUES (
        v_store_id, v_org_id, 'YRV-MAIN', 'YRV Andheri Flagship',
        v_store_type_id, v_wh_id,
        '12 Innovation Park, Andheri East', 'Mumbai', 'Maharashtra', '9876543210',
        'ACTIVE', v_user_id, v_user_id
    );

    INSERT INTO retail_cash_counters (
        id, organization_id, store_id, code, name, status, created_by, updated_by
    ) VALUES (
        v_counter_id, v_org_id, v_store_id, 'C1', 'Counter 1', 'ACTIVE', v_user_id, v_user_id
    );

    INSERT INTO retail_terminals (
        id, organization_id, store_id, counter_id, code, name, status, created_by, updated_by
    ) VALUES (
        v_terminal_id, v_org_id, v_store_id, v_counter_id, 'T1', 'POS Terminal 1',
        'ACTIVE', v_user_id, v_user_id
    );

    INSERT INTO retail_cashiers (
        id, organization_id, store_id, user_id, employee_code, display_name, status,
        created_by, updated_by
    ) VALUES (
        v_cashier_id, v_org_id, v_store_id, v_user_id, 'CSH-001', 'Kashyap Admin',
        'ACTIVE', v_user_id, v_user_id
    );

    INSERT INTO retail_shifts (
        id, organization_id, store_id, counter_id, terminal_id, cashier_id,
        status, opened_at, opening_float, notes, created_by, updated_by
    ) VALUES (
        v_shift_id, v_org_id, v_store_id, v_counter_id, v_terminal_id, v_cashier_id,
        'OPEN', NOW() - INTERVAL '2 hours', 5000.00,
        'YRV demo open shift', v_user_id, v_user_id
    );

    -- Catalog masters
    INSERT INTO retail_brands (id, organization_id, code, name, created_by, updated_by)
    VALUES (v_brand_id, v_org_id, 'YRV-TECH', 'YRV Tech', v_user_id, v_user_id);

    INSERT INTO retail_departments (id, organization_id, code, name, created_by, updated_by)
    VALUES (v_dept_id, v_org_id, 'ELECTRONICS', 'Electronics', v_user_id, v_user_id);

    INSERT INTO retail_collections (id, organization_id, code, name, season, created_by, updated_by)
    VALUES (v_coll_id, v_org_id, 'SS26', 'Spring Summer 26', 'SS26', v_user_id, v_user_id);

    -- Price list
    INSERT INTO retail_price_lists (
        id, organization_id, code, name, price_type, currency, active, created_by, updated_by
    ) VALUES (
        v_price_list_id, v_org_id, 'RETAIL-STD', 'Standard Retail', 'RETAIL', 'INR', TRUE,
        v_user_id, v_user_id
    );

    INSERT INTO retail_store_price_lists (organization_id, store_id, price_list_id)
    VALUES (v_org_id, v_store_id, v_price_list_id);

    -- Prefer inventory products (V23); fall back to any active PRODUCT
    FOR v_prod IN
        SELECT p.id, p.name, p.sku, p.selling_price, p.barcode
        FROM products p
        WHERE p.organization_id = v_org_id
          AND p.active = TRUE
          AND p.item_type = 'PRODUCT'
        ORDER BY CASE WHEN p.sku LIKE 'PRD-INV-%' THEN 0 ELSE 1 END, p.sku
        LIMIT 12
    LOOP
        v_i := v_i + 1;
        v_barcode := COALESCE(
            NULLIF(trim(v_prod.barcode), ''),
            '8901001' || lpad(v_i::text, 6, '0')
        );

        UPDATE products
        SET barcode = v_barcode,
            updated_at = NOW()
        WHERE id = v_prod.id
          AND (barcode IS NULL OR trim(barcode) = '');

        INSERT INTO retail_product_extensions (
            organization_id, product_id, brand_id, department_id, collection_id, season,
            attributes_json, created_by, updated_by
        ) VALUES (
            v_org_id, v_prod.id, v_brand_id, v_dept_id, v_coll_id, 'SS26',
            jsonb_build_object('demo', true, 'channel', 'POS'),
            v_user_id, v_user_id
        )
        ON CONFLICT (product_id) DO NOTHING;

        INSERT INTO retail_product_barcodes (
            organization_id, product_id, barcode, barcode_type, is_primary,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_prod.id, v_barcode, 'EAN13', TRUE, v_user_id, v_user_id
        )
        ON CONFLICT (organization_id, barcode) DO NOTHING;

        INSERT INTO retail_price_list_items (
            organization_id, price_list_id, product_id, unit_price, min_qty,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_price_list_id, v_prod.id,
            COALESCE(NULLIF(v_prod.selling_price, 0), 999.00), 1,
            v_user_id, v_user_id
        );
    END LOOP;

    IF v_i = 0 THEN
        RAISE NOTICE 'V49 warning: no products found — store/shift seeded without catalog links';
    END IF;

    -- Loyalty tiers
    INSERT INTO retail_loyalty_tiers (
        organization_id, code, name, min_points, earn_rate, created_by, updated_by
    ) VALUES
        (v_org_id, 'BRONZE', 'Bronze', 0, 1.0, v_user_id, v_user_id),
        (v_org_id, 'SILVER', 'Silver', 1000, 1.25, v_user_id, v_user_id),
        (v_org_id, 'GOLD', 'Gold', 5000, 1.5, v_user_id, v_user_id)
    ON CONFLICT (organization_id, code) DO NOTHING;

    -- Store inventory location (table from V47)
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'retail_inventory_locations'
    ) INTO v_has_inv_locations;

    IF v_has_inv_locations THEN
        INSERT INTO retail_inventory_locations (
            organization_id, store_id, warehouse_id, code, name, location_type,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_store_id, v_wh_id, 'FLOOR', 'Sales Floor', 'SHELF',
            v_user_id, v_user_id
        );
    END IF;

    SELECT id INTO v_customer_id
    FROM customers
    WHERE organization_id = v_org_id
    ORDER BY created_at
    LIMIT 1;

    SELECT p.id,
           p.name,
           COALESCE(NULLIF(p.selling_price, 0), 999),
           COALESCE(NULLIF(trim(p.barcode), ''), '8901001000001')
    INTO v_product_id, v_product_name, v_rate, v_product_barcode
    FROM products p
    WHERE p.organization_id = v_org_id
      AND p.active = TRUE
      AND p.item_type = 'PRODUCT'
    ORDER BY CASE WHEN p.sku LIKE 'PRD-INV-%' THEN 0 ELSE 1 END, p.sku
    LIMIT 1;

    IF v_product_id IS NOT NULL THEN
        v_tax := round(v_rate * 0.18, 2);
        v_subtotal := round(v_rate, 2);
        v_grand := v_subtotal + v_tax;

        INSERT INTO pos_sales (
            id, organization_id, store_id, counter_id, terminal_id, shift_id, cashier_id,
            customer_id, status, receipt_type, bill_number,
            subtotal, discount_total, tax_total, grand_total, held_label, notes,
            created_by, updated_by
        ) VALUES (
            v_sale_held_id, v_org_id, v_store_id, v_counter_id, v_terminal_id, v_shift_id, v_cashier_id,
            v_customer_id, 'HELD', 'POS_RECEIPT', NULL,
            v_subtotal, 0, v_tax, v_grand, 'Customer will return', 'YRV demo held cart',
            v_user_id, v_user_id
        );

        INSERT INTO pos_sale_lines (
            organization_id, pos_sale_id, product_id, description, barcode,
            quantity, rate, discount_percent, tax_rate, line_total, line_order,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_sale_held_id, v_product_id, v_product_name, v_product_barcode,
            1, v_rate, 0, 18, v_grand, 0, v_user_id, v_user_id
        );

        -- Completed walk-in (demo history; no core invoice link)
        INSERT INTO pos_sales (
            id, organization_id, store_id, counter_id, terminal_id, shift_id, cashier_id,
            customer_id, status, receipt_type, bill_number,
            subtotal, discount_total, tax_total, grand_total, notes, completed_at,
            created_by, updated_by
        ) VALUES (
            v_sale_done_id, v_org_id, v_store_id, v_counter_id, v_terminal_id, v_shift_id, v_cashier_id,
            v_customer_id, 'COMPLETED', 'POS_RECEIPT', 'POS/YRV/000001',
            v_subtotal, 0, v_tax, v_grand, 'YRV demo completed sale', NOW() - INTERVAL '45 minutes',
            v_user_id, v_user_id
        );

        INSERT INTO pos_sale_lines (
            organization_id, pos_sale_id, product_id, description, barcode,
            quantity, rate, discount_percent, tax_rate, line_total, line_order,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_sale_done_id, v_product_id, v_product_name, v_product_barcode,
            1, v_rate, 0, 18, v_grand, 0, v_user_id, v_user_id
        );

        INSERT INTO pos_sale_payments (
            organization_id, pos_sale_id, payment_mode, amount, reference,
            created_by, updated_by
        ) VALUES (
            v_org_id, v_sale_done_id, 'UPI', v_grand, 'UPI-YRV-DEMO-001', v_user_id, v_user_id
        );
    END IF;

    INSERT INTO retail_promotions (
        organization_id, code, name, promo_type, discount_percent, min_bill_amount,
        coupon_code, starts_at, ends_at, store_id, active, created_by, updated_by
    ) VALUES (
        v_org_id, 'WELCOME10', 'Welcome 10% off', 'PERCENT_OFF', 10.0000, 1000.00,
        'YRV10', NOW() - INTERVAL '7 days', NOW() + INTERVAL '90 days',
        v_store_id, TRUE, v_user_id, v_user_id
    )
    ON CONFLICT (organization_id, code) DO NOTHING;

    RAISE NOTICE 'V49 applied: YRV POS seed (store YRV-MAIN, open shift, % catalog products)', v_i;
END
$yrv_pos$;
