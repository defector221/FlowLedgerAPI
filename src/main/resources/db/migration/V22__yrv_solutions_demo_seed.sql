-- YRV Solutions demo seed: 3 Indian FYs (~₹3 Cr sales / ~₹1.8 Cr purchases each).
-- Login: kashyap221@gmail.com / passwor123d
-- Idempotent: skips if that email already exists.

DO $yrv$
DECLARE
    v_org_id UUID := gen_random_uuid();
    v_user_id UUID := gen_random_uuid();
    v_membership_id UUID := gen_random_uuid();
    v_role_id UUID;
    v_plan_id UUID;
    v_unit_id UUID;
    v_wh_id UUID := gen_random_uuid();
    v_tax_id UUID := gen_random_uuid();
    v_pwd TEXT := '$2a$10$LocS.KbCEzMuSH0BWTyQquxoDOb0MbT5Mlm7lVkAHVOT3lVudK3ym';

    v_acc_cash UUID := gen_random_uuid();
    v_acc_bank UUID := gen_random_uuid();
    v_acc_ar UUID := gen_random_uuid();
    v_acc_inv UUID := gen_random_uuid();
    v_acc_icgst UUID := gen_random_uuid();
    v_acc_isgst UUID := gen_random_uuid();
    v_acc_iigst UUID := gen_random_uuid();
    v_acc_ap UUID := gen_random_uuid();
    v_acc_ocgst UUID := gen_random_uuid();
    v_acc_osgst UUID := gen_random_uuid();
    v_acc_oigst UUID := gen_random_uuid();
    v_acc_cap UUID := gen_random_uuid();
    v_acc_re UUID := gen_random_uuid();
    v_acc_sales UUID := gen_random_uuid();
    v_acc_oi UUID := gen_random_uuid();
    v_acc_dr UUID := gen_random_uuid();
    v_acc_roi UUID := gen_random_uuid();
    v_acc_purch UUID := gen_random_uuid();
    v_acc_cogs UUID := gen_random_uuid();
    v_acc_opex UUID := gen_random_uuid();
    v_acc_da UUID := gen_random_uuid();
    v_acc_roe UUID := gen_random_uuid();

    v_customer_ids UUID[] := ARRAY[]::UUID[];
    v_supplier_ids UUID[] := ARRAY[]::UUID[];
    v_sales_product_ids UUID[] := ARRAY[]::UUID[];
    v_purch_product_ids UUID[] := ARRAY[]::UUID[];
    v_cid UUID;
    v_sid UUID;
    v_pid UUID;

    v_fy_names TEXT[] := ARRAY['2023-24', '2024-25', '2025-26', '2026-27'];
    v_fy_starts DATE[] := ARRAY['2023-04-01'::DATE, '2024-04-01'::DATE, '2025-04-01'::DATE, '2026-04-01'::DATE];
    v_fy_ends DATE[] := ARRAY['2024-03-31'::DATE, '2025-03-31'::DATE, '2026-03-31'::DATE, '2027-03-31'::DATE];
    v_fy_id UUID;
    v_period_id UUID;
    v_period_start DATE;
    v_period_end DATE;
    v_fy_idx INT;
    v_month_idx INT;
    v_inv_idx INT;
    v_je_seq BIGINT := 0;
    v_pay_seq BIGINT := 0;
    v_si_seq BIGINT;
    v_pi_seq BIGINT;

    v_inv_date DATE;
    v_si_id UUID;
    v_pi_id UUID;
    v_je_id UUID;
    v_pay_id UUID;
    v_taxable NUMERIC(18,2);
    v_cgst NUMERIC(18,2);
    v_sgst NUMERIC(18,2);
    v_grand NUMERIC(18,2);
    v_paid NUMERIC(18,2);
    v_rate NUMERIC(18,4);
    v_cust UUID;
    v_supp UUID;
    v_prod UUID;
    v_entry_number TEXT;
    v_inv_number TEXT;
    v_fy_label TEXT;
    v_month_name TEXT;
    v_sales_names TEXT[] := ARRAY[
        'IT Consulting', 'SaaS Subscription', 'Implementation Services', 'Managed Support',
        'Training Workshop', 'Digital Marketing Retainer', 'Cloud Managed Service', 'Custom Development'
    ];
    v_purch_names TEXT[] := ARRAY[
        'Cloud Infrastructure', 'Software Licenses', 'Contractor Services',
        'Office Facilities', 'Marketing Agency', 'Professional Services'
    ];
    v_cust_names TEXT[] := ARRAY[
        'Acme Traders', 'Nova Tech', 'Blue Peak Pvt Ltd', 'Sunrise Retail', 'Orbit Logistics',
        'Pixel Labs', 'Greenfield Foods', 'Summit Healthcare', 'Atlas Motors', 'Cedar Soft',
        'Harbor Exports', 'Lotus Pharma', 'Prime Builders', 'Quantum Edge', 'River Soft',
        'Silverline Media', 'Urban Kart', 'Vector Systems', 'Willow Farms', 'Zenith Apparel'
    ];
    v_supp_names TEXT[] := ARRAY[
        'AWS Reseller IN', 'Azure Partner Hub', 'Infosys Contractors', 'TCS Support Desk',
        'LocalNet ISP', 'OfficeSpace India', 'Pixel Ads Agency', 'SoftLink Licenses',
        'Prime Stationery Co', 'Courier Express', 'InfraOps Pvt Ltd', 'CloudNative Labs',
        'SecureKey Soft', 'DesignCraft Studio', 'Payroll Partners', 'TaxAdvisory LLP',
        'CleanPro Facilities', 'EventCraft India', 'PrintHouse Mumbai', 'Hardware Bazaar'
    ];
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE lower(email) = lower('kashyap221@gmail.com')) THEN
        RAISE NOTICE 'YRV seed skipped: user kashyap221@gmail.com already exists';
        RETURN;
    END IF;

    SELECT id INTO v_role_id FROM roles WHERE code = 'ORGANIZATION_ADMIN' LIMIT 1;
    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'ORGANIZATION_ADMIN role missing — run earlier migrations first';
    END IF;

    SELECT id INTO v_unit_id FROM units WHERE organization_id IS NULL AND code = 'PCS' LIMIT 1;
    IF v_unit_id IS NULL THEN
        RAISE EXCEPTION 'System unit PCS missing';
    END IF;

    SELECT id INTO v_plan_id FROM subscription_plans WHERE code = 'FREE' LIMIT 1;

    -- Organization
    INSERT INTO organizations (
        id, name, legal_name, gstin, pan, email, phone,
        billing_address, city, state, state_code, country, currency,
        financial_year_start, invoice_prefix, purchase_invoice_prefix, payment_prefix,
        invoice_number_format, onboarding_completed, onboarding_completed_at, active
    ) VALUES (
        v_org_id,
        'YRV Solutions',
        'YRV Solutions Private Limited',
        '27AABCY1234D1Z5',
        'AABCY1234D',
        'kashyap221@gmail.com',
        '9876543210',
        '12 Innovation Park, Andheri East',
        'Mumbai',
        'Maharashtra',
        '27',
        'India',
        'INR',
        '04-01',
        'INV',
        'PINV',
        'PAY',
        '{PREFIX}/{FY}/{SEQ:6}',
        TRUE,
        NOW(),
        TRUE
    );

    INSERT INTO organization_settings (organization_id, inventory_deduction_event, tax_inclusive_default, round_off_enabled)
    VALUES (v_org_id, 'INVOICE_CONFIRM', FALSE, TRUE);

    -- User + membership
    INSERT INTO users (
        id, organization_id, last_active_organization_id, email, password_hash,
        first_name, last_name, phone, active, email_verified, user_status
    ) VALUES (
        v_user_id, v_org_id, v_org_id, 'kashyap221@gmail.com', v_pwd,
        'Kashyap', 'Admin', '9876543210', TRUE, TRUE, 'ACTIVE'
    );

    INSERT INTO user_roles (user_id, role_id) VALUES (v_user_id, v_role_id);

    INSERT INTO organization_memberships (id, user_id, organization_id, status, last_active_at)
    VALUES (v_membership_id, v_user_id, v_org_id, 'ACTIVE', NOW());

    INSERT INTO organization_membership_roles (membership_id, role_id)
    VALUES (v_membership_id, v_role_id);

    IF v_plan_id IS NOT NULL THEN
        INSERT INTO user_subscriptions (user_id, plan_id, status, starts_at)
        VALUES (v_user_id, v_plan_id, 'ACTIVE', NOW())
        ON CONFLICT (user_id) DO NOTHING;
    END IF;

    -- Warehouse + tax
    INSERT INTO warehouses (
        id, organization_id, warehouse_code, warehouse_name, address, is_default, active
    ) VALUES (
        v_wh_id, v_org_id, 'WH-MAIN', 'Mumbai Main', 'Andheri East, Mumbai', TRUE, TRUE
    );

    UPDATE organization_settings SET default_warehouse_id = v_wh_id WHERE organization_id = v_org_id;

    INSERT INTO tax_rates (
        id, organization_id, name, rate, cgst_rate, sgst_rate, igst_rate, cess_rate,
        active, tax_type, split_strategy
    ) VALUES (
        v_tax_id, v_org_id, 'GST 18%', 18, 9, 9, 18, 0,
        TRUE, 'GST', 'PLACE_OF_SUPPLY'
    );

    UPDATE organizations SET default_tax_rate_id = v_tax_id WHERE id = v_org_id;

    -- Chart of accounts (aligned with ChartOfAccountsBootstrap)
    INSERT INTO accounts (
        id, organization_id, account_code, account_name, account_type, account_sub_type,
        system_account_key, system_account, active, allow_manual_posting
    ) VALUES
        (v_acc_cash,  v_org_id, '1000', 'Cash',                 'ASSET',     'CASH',                 'CASH',                 TRUE, TRUE, TRUE),
        (v_acc_bank,  v_org_id, '1010', 'Bank',                 'ASSET',     'BANK',                 'BANK',                 TRUE, TRUE, TRUE),
        (v_acc_ar,    v_org_id, '1100', 'Accounts Receivable',  'ASSET',     'ACCOUNTS_RECEIVABLE',  'ACCOUNTS_RECEIVABLE',  TRUE, TRUE, TRUE),
        (v_acc_inv,   v_org_id, '1200', 'Inventory',            'ASSET',     'INVENTORY',            'INVENTORY',            TRUE, TRUE, TRUE),
        (v_acc_icgst, v_org_id, '1300', 'Input CGST',           'ASSET',     'TAX_RECEIVABLE',       'INPUT_CGST',           TRUE, TRUE, TRUE),
        (v_acc_isgst, v_org_id, '1310', 'Input SGST',           'ASSET',     'TAX_RECEIVABLE',       'INPUT_SGST',           TRUE, TRUE, TRUE),
        (v_acc_iigst, v_org_id, '1320', 'Input IGST',           'ASSET',     'TAX_RECEIVABLE',       'INPUT_IGST',           TRUE, TRUE, TRUE),
        (v_acc_ap,    v_org_id, '2000', 'Accounts Payable',     'LIABILITY', 'ACCOUNTS_PAYABLE',     'ACCOUNTS_PAYABLE',     TRUE, TRUE, TRUE),
        (v_acc_ocgst, v_org_id, '2100', 'Output CGST',          'LIABILITY', 'TAX_PAYABLE',          'OUTPUT_CGST',          TRUE, TRUE, TRUE),
        (v_acc_osgst, v_org_id, '2110', 'Output SGST',          'LIABILITY', 'TAX_PAYABLE',          'OUTPUT_SGST',          TRUE, TRUE, TRUE),
        (v_acc_oigst, v_org_id, '2120', 'Output IGST',          'LIABILITY', 'TAX_PAYABLE',          'OUTPUT_IGST',          TRUE, TRUE, TRUE),
        (v_acc_cap,   v_org_id, '3000', 'Capital',              'EQUITY',    'CAPITAL',              'CAPITAL',              TRUE, TRUE, TRUE),
        (v_acc_re,    v_org_id, '3100', 'Retained Earnings',    'EQUITY',    'RETAINED_EARNINGS',    'RETAINED_EARNINGS',    TRUE, TRUE, TRUE),
        (v_acc_sales, v_org_id, '4000', 'Sales',                'REVENUE',   'SALES',                'SALES',                TRUE, TRUE, TRUE),
        (v_acc_oi,    v_org_id, '4100', 'Other Income',         'REVENUE',   'INDIRECT_INCOME',      'OTHER_INCOME',         TRUE, TRUE, TRUE),
        (v_acc_dr,    v_org_id, '4200', 'Discount Received',    'REVENUE',   'DISCOUNT_RECEIVED',    'DISCOUNT_RECEIVED',    TRUE, TRUE, TRUE),
        (v_acc_roi,   v_org_id, '4300', 'Round Off Income',     'REVENUE',   'ROUND_OFF',            'ROUND_OFF_INCOME',     TRUE, TRUE, TRUE),
        (v_acc_purch, v_org_id, '5000', 'Purchases',            'EXPENSE',   'PURCHASE',             'PURCHASE',             TRUE, TRUE, TRUE),
        (v_acc_cogs,  v_org_id, '5100', 'Cost of Goods Sold',   'EXPENSE',   'COST_OF_GOODS_SOLD',   'COGS',                 TRUE, TRUE, TRUE),
        (v_acc_opex,  v_org_id, '5200', 'Operating Expenses',   'EXPENSE',   'INDIRECT_EXPENSE',     'OPERATING_EXPENSES',   TRUE, TRUE, TRUE),
        (v_acc_da,    v_org_id, '5300', 'Discount Allowed',     'EXPENSE',   'DISCOUNT_ALLOWED',     'DISCOUNT_ALLOWED',     TRUE, TRUE, TRUE),
        (v_acc_roe,   v_org_id, '5400', 'Round Off Expense',    'EXPENSE',   'ROUND_OFF',            'ROUND_OFF_EXPENSE',    TRUE, TRUE, TRUE);

    -- Opening capital (optional liquid funds for cash/bank UI)
    -- skipped: P&L driven by invoices

    -- Customers
    FOR i IN 1..20 LOOP
        v_cid := gen_random_uuid();
        v_customer_ids := array_append(v_customer_ids, v_cid);
        INSERT INTO customers (
            id, organization_id, customer_code, customer_name, company_name,
            email, phone, city, state, state_code, country, payment_terms
        ) VALUES (
            v_cid, v_org_id,
            'CUST-' || lpad(i::text, 2, '0'),
            v_cust_names[i],
            v_cust_names[i],
            'customer' || i || '@yrv.demo',
            '98' || lpad((10000000 + i)::text, 8, '0'),
            'Mumbai', 'Maharashtra', '27', 'India', 'Net 30'
        );
    END LOOP;

    -- Suppliers
    FOR i IN 1..20 LOOP
        v_sid := gen_random_uuid();
        v_supplier_ids := array_append(v_supplier_ids, v_sid);
        INSERT INTO suppliers (
            id, organization_id, supplier_code, supplier_name, company_name,
            email, phone, city, state, state_code, country, payment_terms
        ) VALUES (
            v_sid, v_org_id,
            'SUP-' || lpad(i::text, 2, '0'),
            v_supp_names[i],
            v_supp_names[i],
            'supplier' || i || '@yrv.demo',
            '97' || lpad((10000000 + i)::text, 8, '0'),
            'Mumbai', 'Maharashtra', '27', 'India', 'Net 30'
        );
    END LOOP;

    -- Products (SERVICE)
    FOR i IN 1..array_length(v_sales_names, 1) LOOP
        v_pid := gen_random_uuid();
        v_sales_product_ids := array_append(v_sales_product_ids, v_pid);
        INSERT INTO products (
            id, organization_id, item_type, sku, name, unit_id, tax_rate_id,
            selling_price, purchase_price, hsn_sac_code, active
        ) VALUES (
            v_pid, v_org_id, 'SERVICE',
            'SVC-S-' || lpad(i::text, 2, '0'),
            v_sales_names[i],
            v_unit_id, v_tax_id,
            100000 + i * 5000, 0, '998314', TRUE
        );
    END LOOP;

    FOR i IN 1..array_length(v_purch_names, 1) LOOP
        v_pid := gen_random_uuid();
        v_purch_product_ids := array_append(v_purch_product_ids, v_pid);
        INSERT INTO products (
            id, organization_id, item_type, sku, name, unit_id, tax_rate_id,
            selling_price, purchase_price, hsn_sac_code, active
        ) VALUES (
            v_pid, v_org_id, 'SERVICE',
            'SVC-P-' || lpad(i::text, 2, '0'),
            v_purch_names[i],
            v_unit_id, v_tax_id,
            0, 80000 + i * 4000, '998399', TRUE
        );
    END LOOP;

    -- Fiscal years + monthly periods (2026-27 is current, no seed invoices)
    FOR v_fy_idx IN 1..4 LOOP
        v_fy_id := gen_random_uuid();
        INSERT INTO fiscal_years (
            id, organization_id, name, start_date, end_date, status, is_current
        ) VALUES (
            v_fy_id, v_org_id, v_fy_names[v_fy_idx], v_fy_starts[v_fy_idx], v_fy_ends[v_fy_idx],
            'OPEN', (v_fy_idx = 4)
        );

        v_period_start := v_fy_starts[v_fy_idx];
        FOR v_month_idx IN 1..12 LOOP
            v_period_end := (v_period_start + INTERVAL '1 month' - INTERVAL '1 day')::DATE;
            IF v_period_end > v_fy_ends[v_fy_idx] THEN
                v_period_end := v_fy_ends[v_fy_idx];
            END IF;
            v_month_name := to_char(v_period_start, 'Mon YYYY');
            INSERT INTO accounting_periods (
                organization_id, fiscal_year_id, period_number, name, start_date, end_date, status
            ) VALUES (
                v_org_id, v_fy_id, v_month_idx, v_month_name, v_period_start, v_period_end, 'OPEN'
            );
            v_period_start := (v_period_end + INTERVAL '1 day')::DATE;
            EXIT WHEN v_period_start > v_fy_ends[v_fy_idx];
        END LOOP;

        -- Document sequences for later app numbering
        INSERT INTO document_sequences (organization_id, document_type, financial_year, prefix, next_value)
        VALUES
            (v_org_id, 'SALES_INVOICE', v_fy_names[v_fy_idx], 'INV', CASE WHEN v_fy_idx <= 3 THEN 151 ELSE 1 END),
            (v_org_id, 'PURCHASE_INVOICE', v_fy_names[v_fy_idx], 'PINV', CASE WHEN v_fy_idx <= 3 THEN 121 ELSE 1 END),
            (v_org_id, 'PAYMENT', v_fy_names[v_fy_idx], 'PAY', CASE WHEN v_fy_idx <= 3 THEN 201 ELSE 1 END),
            (v_org_id, 'JOURNAL', v_fy_names[v_fy_idx], 'JV', 1)
        ON CONFLICT (organization_id, document_type, financial_year) DO NOTHING;
    END LOOP;

    -- Seed invoices for first 3 FYs only
    FOR v_fy_idx IN 1..3 LOOP
        v_fy_label := v_fy_names[v_fy_idx];
        v_si_seq := 0;
        v_pi_seq := 0;
        v_period_start := v_fy_starts[v_fy_idx];

        FOR v_month_idx IN 1..12 LOOP
            v_period_end := (v_period_start + INTERVAL '1 month' - INTERVAL '1 day')::DATE;
            IF v_period_end > v_fy_ends[v_fy_idx] THEN
                v_period_end := v_fy_ends[v_fy_idx];
            END IF;

            SELECT id, fiscal_year_id INTO v_period_id, v_fy_id
            FROM accounting_periods
            WHERE organization_id = v_org_id
              AND start_date = v_period_start
            LIMIT 1;

            -- 12 sales invoices × ~₹2,08,333.33 taxable ≈ ₹25L / month → ₹3 Cr / FY
            FOR v_inv_idx IN 1..12 LOOP
                v_si_seq := v_si_seq + 1;
                v_taxable := 208333.33;
                -- last invoice of FY absorbs rounding to hit exactly 3,00,00,000
                IF v_month_idx = 12 AND v_inv_idx = 12 THEN
                    v_taxable := 30000000.00 - (11 * 12 + 11) * 208333.33;
                END IF;
                v_cgst := round(v_taxable * 0.09, 2);
                v_sgst := round(v_taxable * 0.09, 2);
                v_grand := v_taxable + v_cgst + v_sgst;
                v_inv_date := v_period_start + LEAST(v_inv_idx + 1, (v_period_end - v_period_start));
                v_cust := v_customer_ids[1 + ((v_inv_idx + v_month_idx - 1) % 20)];
                v_prod := v_sales_product_ids[1 + ((v_inv_idx - 1) % array_length(v_sales_product_ids, 1))];
                v_rate := v_taxable;
                v_si_id := gen_random_uuid();
                v_inv_number := 'INV/' || v_fy_label || '/' || lpad(v_si_seq::text, 6, '0');

                INSERT INTO sales_invoices (
                    id, organization_id, invoice_number, invoice_date, due_date, customer_id, warehouse_id,
                    place_of_supply, customer_gstin, status, payment_status,
                    subtotal, taxable_amount, cgst_total, sgst_total, igst_total,
                    grand_total, amount_paid, outstanding_amount,
                    accounting_status, accounting_posted_at, created_by
                ) VALUES (
                    v_si_id, v_org_id, v_inv_number, v_inv_date, v_inv_date + 30, v_cust, v_wh_id,
                    'Maharashtra', NULL, 'CONFIRMED', 'UNPAID',
                    v_taxable, v_taxable, v_cgst, v_sgst, 0,
                    v_grand, 0, v_grand,
                    'POSTED', NOW(), v_user_id
                );

                INSERT INTO sales_invoice_items (
                    sales_invoice_id, product_id, description, quantity, unit_id, rate,
                    tax_rate, taxable_amount, cgst_rate, sgst_rate, igst_rate,
                    cgst_amount, sgst_amount, igst_amount, line_total, line_order,
                    tax_type, split_strategy
                ) VALUES (
                    v_si_id, v_prod, 'Professional services', 1, v_unit_id, v_rate,
                    18, v_taxable, 9, 9, 0,
                    v_cgst, v_sgst, 0, v_grand, 1,
                    'GST', 'PLACE_OF_SUPPLY'
                );

                -- Journal SI
                v_je_seq := v_je_seq + 1;
                v_je_id := gen_random_uuid();
                v_entry_number := 'JV/' || v_fy_label || '/' || lpad(v_je_seq::text, 6, '0');
                INSERT INTO journal_entries (
                    id, organization_id, fiscal_year_id, accounting_period_id,
                    entry_number, entry_date, posting_date, reference_type, reference_id,
                    voucher_type, voucher_number, description, status, source,
                    total_debit, total_credit, posted_at, posted_by, created_by
                ) VALUES (
                    v_je_id, v_org_id, v_fy_id, v_period_id,
                    v_entry_number, v_inv_date, v_inv_date, 'SALES_INVOICE', v_si_id,
                    'SALES', v_inv_number, 'Sales invoice ' || v_inv_number, 'POSTED', 'SALES_INVOICE',
                    v_grand, v_grand, NOW(), v_user_id, v_user_id
                );

                INSERT INTO journal_entry_lines (
                    organization_id, journal_entry_id, account_id, line_number, description,
                    debit_amount, credit_amount, customer_id, created_by
                ) VALUES
                    (v_org_id, v_je_id, v_acc_ar, 1, 'AR ' || v_inv_number, v_grand, 0, v_cust, v_user_id),
                    (v_org_id, v_je_id, v_acc_sales, 2, 'Sales ' || v_inv_number, 0, v_taxable, v_cust, v_user_id),
                    (v_org_id, v_je_id, v_acc_ocgst, 3, 'Output CGST', 0, v_cgst, NULL, v_user_id),
                    (v_org_id, v_je_id, v_acc_osgst, 4, 'Output SGST', 0, v_sgst, NULL, v_user_id);

                UPDATE sales_invoices SET posted_journal_entry_id = v_je_id WHERE id = v_si_id;

                -- ~75% receipts (skip every 4th)
                IF (v_inv_idx % 4) <> 0 THEN
                    v_paid := round(v_grand * 0.75, 2);
                    v_pay_seq := v_pay_seq + 1;
                    v_pay_id := gen_random_uuid();
                    INSERT INTO payments (
                        id, organization_id, payment_number, payment_date, payment_type, party_type,
                        customer_id, amount, payment_mode, notes,
                        accounting_status, accounting_posted_at, created_by
                    ) VALUES (
                        v_pay_id, v_org_id,
                        'PAY/' || v_fy_label || '/' || lpad(v_pay_seq::text, 6, '0'),
                        v_inv_date + 7, 'RECEIPT', 'CUSTOMER',
                        v_cust, v_paid, 'BANK_TRANSFER', 'Seed receipt',
                        'POSTED', NOW(), v_user_id
                    );
                    INSERT INTO payment_allocations (payment_id, document_type, document_id, allocated_amount)
                    VALUES (v_pay_id, 'SALES_INVOICE', v_si_id, v_paid);

                    UPDATE sales_invoices SET
                        amount_paid = v_paid,
                        outstanding_amount = v_grand - v_paid,
                        payment_status = 'PARTIALLY_PAID'
                    WHERE id = v_si_id;

                    v_je_seq := v_je_seq + 1;
                    v_je_id := gen_random_uuid();
                    v_entry_number := 'JV/' || v_fy_label || '/' || lpad(v_je_seq::text, 6, '0');
                    INSERT INTO journal_entries (
                        id, organization_id, fiscal_year_id, accounting_period_id,
                        entry_number, entry_date, posting_date, reference_type, reference_id,
                        voucher_type, description, status, source,
                        total_debit, total_credit, posted_at, posted_by, created_by
                    ) VALUES (
                        v_je_id, v_org_id, v_fy_id, v_period_id,
                        v_entry_number, v_inv_date + 7, v_inv_date + 7, 'PAYMENT', v_pay_id,
                        'RECEIPT', 'Customer receipt', 'POSTED', 'CUSTOMER_RECEIPT',
                        v_paid, v_paid, NOW(), v_user_id, v_user_id
                    );
                    INSERT INTO journal_entry_lines (
                        organization_id, journal_entry_id, account_id, line_number, description,
                        debit_amount, credit_amount, customer_id, created_by
                    ) VALUES
                        (v_org_id, v_je_id, v_acc_bank, 1, 'Bank receipt', v_paid, 0, v_cust, v_user_id),
                        (v_org_id, v_je_id, v_acc_ar, 2, 'AR settlement', 0, v_paid, v_cust, v_user_id);
                    UPDATE payments SET posted_journal_entry_id = v_je_id WHERE id = v_pay_id;
                END IF;
            END LOOP;

            -- 10 purchase invoices × ₹1,50,000 taxable ≈ ₹15L / month → ₹1.8 Cr / FY
            FOR v_inv_idx IN 1..10 LOOP
                v_pi_seq := v_pi_seq + 1;
                v_taxable := 150000.00;
                IF v_month_idx = 12 AND v_inv_idx = 10 THEN
                    v_taxable := 18000000.00 - (11 * 10 + 9) * 150000.00;
                END IF;
                v_cgst := round(v_taxable * 0.09, 2);
                v_sgst := round(v_taxable * 0.09, 2);
                v_grand := v_taxable + v_cgst + v_sgst;
                v_inv_date := v_period_start + LEAST(v_inv_idx + 3, (v_period_end - v_period_start));
                v_supp := v_supplier_ids[1 + ((v_inv_idx + v_month_idx - 1) % 20)];
                v_prod := v_purch_product_ids[1 + ((v_inv_idx - 1) % array_length(v_purch_product_ids, 1))];
                v_rate := v_taxable;
                v_pi_id := gen_random_uuid();
                v_inv_number := 'PINV/' || v_fy_label || '/' || lpad(v_pi_seq::text, 6, '0');

                INSERT INTO purchase_invoices (
                    id, organization_id, invoice_number, supplier_invoice_number, invoice_date, due_date,
                    supplier_id, warehouse_id, place_of_supply, status, payment_status,
                    subtotal, taxable_amount, cgst_total, sgst_total, igst_total,
                    grand_total, amount_paid, outstanding_amount,
                    accounting_status, accounting_posted_at, created_by
                ) VALUES (
                    v_pi_id, v_org_id, v_inv_number, 'SUP-' || v_inv_number, v_inv_date, v_inv_date + 30,
                    v_supp, v_wh_id, 'Maharashtra', 'CONFIRMED', 'UNPAID',
                    v_taxable, v_taxable, v_cgst, v_sgst, 0,
                    v_grand, 0, v_grand,
                    'POSTED', NOW(), v_user_id
                );

                INSERT INTO purchase_invoice_items (
                    purchase_invoice_id, product_id, description, quantity, unit_id, rate,
                    tax_rate, taxable_amount, cgst_rate, sgst_rate, igst_rate,
                    cgst_amount, sgst_amount, igst_amount, line_total, line_order,
                    tax_type, split_strategy
                ) VALUES (
                    v_pi_id, v_prod, 'Purchased services', 1, v_unit_id, v_rate,
                    18, v_taxable, 9, 9, 0,
                    v_cgst, v_sgst, 0, v_grand, 1,
                    'GST', 'PLACE_OF_SUPPLY'
                );

                v_je_seq := v_je_seq + 1;
                v_je_id := gen_random_uuid();
                v_entry_number := 'JV/' || v_fy_label || '/' || lpad(v_je_seq::text, 6, '0');
                INSERT INTO journal_entries (
                    id, organization_id, fiscal_year_id, accounting_period_id,
                    entry_number, entry_date, posting_date, reference_type, reference_id,
                    voucher_type, voucher_number, description, status, source,
                    total_debit, total_credit, posted_at, posted_by, created_by
                ) VALUES (
                    v_je_id, v_org_id, v_fy_id, v_period_id,
                    v_entry_number, v_inv_date, v_inv_date, 'PURCHASE_INVOICE', v_pi_id,
                    'PURCHASE', v_inv_number, 'Purchase invoice ' || v_inv_number, 'POSTED', 'PURCHASE_INVOICE',
                    v_grand, v_grand, NOW(), v_user_id, v_user_id
                );

                INSERT INTO journal_entry_lines (
                    organization_id, journal_entry_id, account_id, line_number, description,
                    debit_amount, credit_amount, supplier_id, created_by
                ) VALUES
                    (v_org_id, v_je_id, v_acc_purch, 1, 'Purchases', v_taxable, 0, v_supp, v_user_id),
                    (v_org_id, v_je_id, v_acc_icgst, 2, 'Input CGST', v_cgst, 0, NULL, v_user_id),
                    (v_org_id, v_je_id, v_acc_isgst, 3, 'Input SGST', v_sgst, 0, NULL, v_user_id),
                    (v_org_id, v_je_id, v_acc_ap, 4, 'AP', 0, v_grand, v_supp, v_user_id);

                UPDATE purchase_invoices SET posted_journal_entry_id = v_je_id WHERE id = v_pi_id;

                -- ~80% supplier payments (skip every 5th)
                IF (v_inv_idx % 5) <> 0 THEN
                    v_paid := round(v_grand * 0.80, 2);
                    v_pay_seq := v_pay_seq + 1;
                    v_pay_id := gen_random_uuid();
                    INSERT INTO payments (
                        id, organization_id, payment_number, payment_date, payment_type, party_type,
                        supplier_id, amount, payment_mode, notes,
                        accounting_status, accounting_posted_at, created_by
                    ) VALUES (
                        v_pay_id, v_org_id,
                        'PAY/' || v_fy_label || '/' || lpad(v_pay_seq::text, 6, '0'),
                        v_inv_date + 10, 'PAYMENT', 'SUPPLIER',
                        v_supp, v_paid, 'BANK_TRANSFER', 'Seed payment',
                        'POSTED', NOW(), v_user_id
                    );
                    INSERT INTO payment_allocations (payment_id, document_type, document_id, allocated_amount)
                    VALUES (v_pay_id, 'PURCHASE_INVOICE', v_pi_id, v_paid);

                    UPDATE purchase_invoices SET
                        amount_paid = v_paid,
                        outstanding_amount = v_grand - v_paid,
                        payment_status = 'PARTIALLY_PAID'
                    WHERE id = v_pi_id;

                    v_je_seq := v_je_seq + 1;
                    v_je_id := gen_random_uuid();
                    v_entry_number := 'JV/' || v_fy_label || '/' || lpad(v_je_seq::text, 6, '0');
                    INSERT INTO journal_entries (
                        id, organization_id, fiscal_year_id, accounting_period_id,
                        entry_number, entry_date, posting_date, reference_type, reference_id,
                        voucher_type, description, status, source,
                        total_debit, total_credit, posted_at, posted_by, created_by
                    ) VALUES (
                        v_je_id, v_org_id, v_fy_id, v_period_id,
                        v_entry_number, v_inv_date + 10, v_inv_date + 10, 'PAYMENT', v_pay_id,
                        'PAYMENT', 'Supplier payment', 'POSTED', 'SUPPLIER_PAYMENT',
                        v_paid, v_paid, NOW(), v_user_id, v_user_id
                    );
                    INSERT INTO journal_entry_lines (
                        organization_id, journal_entry_id, account_id, line_number, description,
                        debit_amount, credit_amount, supplier_id, created_by
                    ) VALUES
                        (v_org_id, v_je_id, v_acc_ap, 1, 'AP settlement', v_paid, 0, v_supp, v_user_id),
                        (v_org_id, v_je_id, v_acc_bank, 2, 'Bank payment', 0, v_paid, v_supp, v_user_id);
                    UPDATE payments SET posted_journal_entry_id = v_je_id WHERE id = v_pay_id;
                END IF;
            END LOOP;

            -- bump JOURNAL sequence after FY month
            UPDATE document_sequences
            SET next_value = GREATEST(next_value, v_je_seq + 1)
            WHERE organization_id = v_org_id AND document_type = 'JOURNAL' AND financial_year = v_fy_label;

            v_period_start := (v_period_end + INTERVAL '1 day')::DATE;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'YRV Solutions seed complete for org % (user kashyap221@gmail.com)', v_org_id;
END
$yrv$;
