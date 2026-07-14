-- Extend YRV Solutions seed: PRODUCT inventory + Quotation → SO → Delivery Challan chain.
-- Applies only when YRV org exists (from V22). Idempotent if PRD-INV-01 already present.

DO $yrv23$
DECLARE
    v_org_id UUID;
    v_user_id UUID;
    v_wh_id UUID;
    v_unit_id UUID;
    v_tax_id UUID;
    v_customer_ids UUID[];
    v_product_ids UUID[] := ARRAY[]::UUID[];
    v_pid UUID;
    v_cust UUID;
    v_q_id UUID;
    v_so_id UUID;
    v_dc_id UUID;
    v_fy TEXT;
    v_fy_start DATE;
    v_month INT;
    v_seq INT;
    v_date DATE;
    v_qty NUMERIC(18,4);
    v_rate NUMERIC(18,4);
    v_taxable NUMERIC(18,2);
    v_cgst NUMERIC(18,2);
    v_sgst NUMERIC(18,2);
    v_tax NUMERIC(18,2);
    v_grand NUMERIC(18,2);
    v_names TEXT[] := ARRAY[
        'Laptop Pro 14', 'Wireless Keyboard', 'USB-C Hub', 'Monitor 27in',
        'Network Switch 24P', 'Webcam HD', 'SSD 1TB', 'Desk Dock Station'
    ];
    v_costs NUMERIC[] := ARRAY[45000, 2500, 1800, 22000, 15000, 3200, 6500, 8900];
    v_prices NUMERIC[] := ARRAY[62000, 3990, 2490, 28900, 19800, 4490, 8900, 12400];
    i INT;
BEGIN
    SELECT o.id INTO v_org_id
    FROM organizations o
    WHERE o.name = 'YRV Solutions'
    ORDER BY o.created_at DESC
    LIMIT 1;

    IF v_org_id IS NULL THEN
        RAISE NOTICE 'V23 skipped: YRV Solutions organization not found';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM products
        WHERE organization_id = v_org_id AND sku = 'PRD-INV-01'
    ) THEN
        RAISE NOTICE 'V23 skipped: PRODUCT inventory seed already present for YRV';
        RETURN;
    END IF;

    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.organization_id = v_org_id OR u.last_active_organization_id = v_org_id
    ORDER BY CASE WHEN lower(u.email) = lower('kashyap221@gmail.com') THEN 0 ELSE 1 END
    LIMIT 1;

    SELECT id INTO v_wh_id
    FROM warehouses
    WHERE organization_id = v_org_id AND is_default = TRUE
    LIMIT 1;
    IF v_wh_id IS NULL THEN
        SELECT id INTO v_wh_id FROM warehouses WHERE organization_id = v_org_id LIMIT 1;
    END IF;

    SELECT id INTO v_unit_id FROM units WHERE organization_id IS NULL AND code = 'PCS' LIMIT 1;
    SELECT id INTO v_tax_id FROM tax_rates WHERE organization_id = v_org_id ORDER BY created_at LIMIT 1;

    IF v_wh_id IS NULL OR v_unit_id IS NULL OR v_tax_id IS NULL THEN
        RAISE EXCEPTION 'V23 aborted: warehouse/unit/tax missing for YRV org %', v_org_id;
    END IF;

    SELECT array_agg(id ORDER BY customer_code)
    INTO v_customer_ids
    FROM customers
    WHERE organization_id = v_org_id;

    IF v_customer_ids IS NULL OR array_length(v_customer_ids, 1) IS NULL THEN
        RAISE EXCEPTION 'V23 aborted: no customers for YRV org';
    END IF;

    -- PRODUCT catalog + opening stock
    FOR i IN 1..array_length(v_names, 1) LOOP
        v_pid := gen_random_uuid();
        v_product_ids := array_append(v_product_ids, v_pid);
        INSERT INTO products (
            id, organization_id, item_type, sku, name, unit_id, tax_rate_id,
            purchase_price, selling_price, mrp, hsn_sac_code,
            opening_stock, minimum_stock_level, reorder_level, active
        ) VALUES (
            v_pid, v_org_id, 'PRODUCT',
            'PRD-INV-' || lpad(i::text, 2, '0'),
            v_names[i],
            v_unit_id, v_tax_id,
            v_costs[i], v_prices[i], v_prices[i] * 1.1, '847130',
            200, 20, 40, TRUE
        );

        INSERT INTO inventory_transactions (
            organization_id, product_id, warehouse_id, transaction_type, transaction_date,
            reference_type, reference_number, inward_qty, outward_qty, unit_cost,
            notes, idempotency_key, created_by
        ) VALUES (
            v_org_id, v_pid, v_wh_id, 'OPENING_STOCK', '2023-04-01',
            'OPENING_STOCK', 'OPEN/YRV/' || lpad(i::text, 2, '0'),
            200, 0, v_costs[i],
            'YRV demo opening stock',
            'yrv-open-' || v_pid::text,
            v_user_id
        );
    END LOOP;

    -- Quotation → SO → Challan: 4 chains per month × 36 months (FY 23-24 .. 25-26)
    FOR v_fy, v_fy_start IN
        SELECT * FROM (VALUES
            ('2023-24', DATE '2023-04-01'),
            ('2024-25', DATE '2024-04-01'),
            ('2025-26', DATE '2025-04-01')
        ) AS t(fy, start_date)
    LOOP
        INSERT INTO document_sequences (organization_id, document_type, financial_year, prefix, next_value)
        VALUES
            (v_org_id, 'QUOTATION', v_fy, 'QT', 49),
            (v_org_id, 'SALES_ORDER', v_fy, 'SO', 49),
            (v_org_id, 'DELIVERY_CHALLAN', v_fy, 'DC', 49)
        ON CONFLICT (organization_id, document_type, financial_year) DO UPDATE
            SET next_value = GREATEST(document_sequences.next_value, EXCLUDED.next_value);

        v_seq := 0;
        FOR v_month IN 0..11 LOOP
            FOR i IN 1..4 LOOP
                v_seq := v_seq + 1;
                v_date := (v_fy_start + (v_month || ' months')::INTERVAL)::DATE + LEAST(i * 2, 25);
                v_cust := v_customer_ids[1 + ((v_seq + v_month) % array_length(v_customer_ids, 1))];
                v_pid := v_product_ids[1 + ((i - 1) % array_length(v_product_ids, 1))];
                v_qty := 2 + (i % 3);
                v_rate := v_prices[1 + ((i - 1) % array_length(v_prices, 1))];
                v_taxable := round(v_qty * v_rate, 2);
                v_cgst := round(v_taxable * 0.09, 2);
                v_sgst := round(v_taxable * 0.09, 2);
                v_tax := v_cgst + v_sgst;
                v_grand := v_taxable + v_tax;

                v_q_id := gen_random_uuid();
                v_so_id := gen_random_uuid();
                v_dc_id := gen_random_uuid();

                INSERT INTO quotations (
                    id, organization_id, quotation_number, customer_id, quotation_date, expiry_date,
                    place_of_supply, status, subtotal, tax_total, grand_total,
                    converted_to_order_id, notes, created_by
                ) VALUES (
                    v_q_id, v_org_id,
                    'QT/' || v_fy || '/' || lpad(v_seq::text, 6, '0'),
                    v_cust, v_date, v_date + 15,
                    'Maharashtra', 'CONVERTED', v_taxable, v_tax, v_grand,
                    v_so_id, 'YRV demo quotation→order', v_user_id
                );

                INSERT INTO quotation_items (
                    quotation_id, product_id, description, quantity, unit_id, rate,
                    tax_rate, taxable_amount, cgst_amount, sgst_amount, igst_amount,
                    line_total, line_order, tax_type, split_strategy
                ) VALUES (
                    v_q_id, v_pid, v_names[1 + ((i - 1) % array_length(v_names, 1))],
                    v_qty, v_unit_id, v_rate,
                    18, v_taxable, v_cgst, v_sgst, 0,
                    v_grand, 1, 'GST', 'PLACE_OF_SUPPLY'
                );

                INSERT INTO sales_orders (
                    id, organization_id, order_number, customer_id, order_date, expected_delivery_date,
                    quotation_id, place_of_supply, status, subtotal, tax_total, grand_total,
                    notes, created_by
                ) VALUES (
                    v_so_id, v_org_id,
                    'SO/' || v_fy || '/' || lpad(v_seq::text, 6, '0'),
                    v_cust, v_date + 1, v_date + 7,
                    v_q_id, 'Maharashtra', 'FULFILLED', v_taxable, v_tax, v_grand,
                    'YRV demo sales order', v_user_id
                );

                INSERT INTO sales_order_items (
                    sales_order_id, product_id, description, quantity, unit_id, rate,
                    tax_rate, taxable_amount, cgst_amount, sgst_amount, igst_amount,
                    line_total, line_order, tax_type, split_strategy
                ) VALUES (
                    v_so_id, v_pid, v_names[1 + ((i - 1) % array_length(v_names, 1))],
                    v_qty, v_unit_id, v_rate,
                    18, v_taxable, v_cgst, v_sgst, 0,
                    v_grand, 1, 'GST', 'PLACE_OF_SUPPLY'
                );

                INSERT INTO delivery_challans (
                    id, organization_id, challan_number, customer_id, sales_order_id,
                    challan_date, warehouse_id, status, notes, created_by
                ) VALUES (
                    v_dc_id, v_org_id,
                    'DC/' || v_fy || '/' || lpad(v_seq::text, 6, '0'),
                    v_cust, v_so_id,
                    v_date + 3, v_wh_id, 'DELIVERED', 'YRV demo delivery', v_user_id
                );

                INSERT INTO delivery_challan_items (
                    delivery_challan_id, product_id, description, quantity, unit_id, line_order
                ) VALUES (
                    v_dc_id, v_pid,
                    v_names[1 + ((i - 1) % array_length(v_names, 1))],
                    v_qty, v_unit_id, 1
                );

                -- Stock out on delivery (SALE) so inventory UI shows movement
                INSERT INTO inventory_transactions (
                    organization_id, product_id, warehouse_id, transaction_type, transaction_date,
                    reference_type, reference_id, reference_number,
                    inward_qty, outward_qty, unit_cost,
                    notes, idempotency_key, created_by
                ) VALUES (
                    v_org_id, v_pid, v_wh_id, 'SALE', v_date + 3,
                    'DELIVERY_CHALLAN', v_dc_id,
                    'DC/' || v_fy || '/' || lpad(v_seq::text, 6, '0'),
                    0, v_qty, v_costs[1 + ((i - 1) % array_length(v_costs, 1))],
                    'Issued on challan',
                    'yrv-dc-sale-' || v_dc_id::text,
                    v_user_id
                );
            END LOOP;
        END LOOP;
    END LOOP;

    -- Restock purchases mid-period so balances stay healthy
    FOR i IN 1..array_length(v_product_ids, 1) LOOP
        INSERT INTO inventory_transactions (
            organization_id, product_id, warehouse_id, transaction_type, transaction_date,
            reference_type, reference_number, inward_qty, outward_qty, unit_cost,
            notes, idempotency_key, created_by
        ) VALUES (
            v_org_id, v_product_ids[i], v_wh_id, 'PURCHASE', '2024-10-01',
            'PURCHASE', 'RESTOCK/YRV/' || lpad(i::text, 2, '0'),
            150, 0, v_costs[i],
            'YRV demo restock',
            'yrv-restock-' || v_product_ids[i]::text,
            v_user_id
        );
    END LOOP;

    RAISE NOTICE 'V23 YRV inventory + QT/SO/DC seed complete for org % (% products, 144 chains)',
        v_org_id, array_length(v_product_ids, 1);
END
$yrv23$;
