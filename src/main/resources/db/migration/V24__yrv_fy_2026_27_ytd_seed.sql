-- Seed FY 2026-27 (Apr–current month) GL activity for YRV so Accounting FYTD/dashboard is non-empty.
-- Idempotent: skips if any INV/2026-27 sales invoice already exists.

DO $yrv24$
DECLARE
    v_org_id UUID;
    v_user_id UUID;
    v_wh_id UUID;
    v_unit_id UUID;
    v_tax_id UUID;
    v_acc_ar UUID;
    v_acc_bank UUID;
    v_acc_sales UUID;
    v_acc_ocgst UUID;
    v_acc_osgst UUID;
    v_acc_purch UUID;
    v_acc_icgst UUID;
    v_acc_isgst UUID;
    v_acc_ap UUID;
    v_customer_ids UUID[];
    v_supplier_ids UUID[];
    v_sales_products UUID[];
    v_purch_products UUID[];
    v_fy TEXT := '2026-27';
    v_fy_id UUID;
    v_period_id UUID;
    v_month_start DATE;
    v_month_end DATE;
    v_today DATE := CURRENT_DATE;
    v_last_month DATE;
    v_si_seq BIGINT := 0;
    v_pi_seq BIGINT := 0;
    v_je_seq BIGINT := 0;
    v_pay_seq BIGINT := 0;
    v_inv_idx INT;
    v_si_id UUID;
    v_pi_id UUID;
    v_je_id UUID;
    v_pay_id UUID;
    v_cust UUID;
    v_supp UUID;
    v_prod UUID;
    v_taxable NUMERIC(18,2);
    v_cgst NUMERIC(18,2);
    v_sgst NUMERIC(18,2);
    v_grand NUMERIC(18,2);
    v_paid NUMERIC(18,2);
    v_inv_date DATE;
    v_entry_number TEXT;
    v_inv_number TEXT;
    v_months INT;
BEGIN
    SELECT o.id INTO v_org_id FROM organizations o WHERE o.name = 'YRV Solutions' ORDER BY o.created_at DESC LIMIT 1;
    IF v_org_id IS NULL THEN
        RAISE NOTICE 'V24 skipped: YRV Solutions not found';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM sales_invoices
        WHERE organization_id = v_org_id AND invoice_number LIKE 'INV/2026-27/%'
    ) THEN
        RAISE NOTICE 'V24 skipped: FY 2026-27 sales already seeded';
        RETURN;
    END IF;

    IF v_today < DATE '2026-04-01' THEN
        RAISE NOTICE 'V24 skipped: current date before FY 2026-27';
        RETURN;
    END IF;

    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.organization_id = v_org_id OR u.last_active_organization_id = v_org_id
    ORDER BY CASE WHEN lower(u.email) = lower('kashyap221@gmail.com') THEN 0 ELSE 1 END
    LIMIT 1;

    SELECT id INTO v_wh_id FROM warehouses WHERE organization_id = v_org_id AND is_default LIMIT 1;
    SELECT id INTO v_unit_id FROM units WHERE organization_id IS NULL AND code = 'PCS' LIMIT 1;
    SELECT id INTO v_tax_id FROM tax_rates WHERE organization_id = v_org_id LIMIT 1;

    SELECT id INTO v_acc_ar FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'ACCOUNTS_RECEIVABLE';
    SELECT id INTO v_acc_bank FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'BANK';
    SELECT id INTO v_acc_sales FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'SALES';
    SELECT id INTO v_acc_ocgst FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'OUTPUT_CGST';
    SELECT id INTO v_acc_osgst FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'OUTPUT_SGST';
    SELECT id INTO v_acc_purch FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'PURCHASE';
    SELECT id INTO v_acc_icgst FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'INPUT_CGST';
    SELECT id INTO v_acc_isgst FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'INPUT_SGST';
    SELECT id INTO v_acc_ap FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'ACCOUNTS_PAYABLE';

    SELECT id INTO v_fy_id FROM fiscal_years WHERE organization_id = v_org_id AND name = v_fy LIMIT 1;
    IF v_fy_id IS NULL THEN
        RAISE EXCEPTION 'V24: fiscal year 2026-27 missing for YRV';
    END IF;

    SELECT array_agg(id ORDER BY customer_code) INTO v_customer_ids FROM customers WHERE organization_id = v_org_id;
    SELECT array_agg(id ORDER BY supplier_code) INTO v_supplier_ids FROM suppliers WHERE organization_id = v_org_id;
    SELECT array_agg(id) INTO v_sales_products FROM products WHERE organization_id = v_org_id AND item_type = 'SERVICE' AND sku LIKE 'SVC-S-%';
    SELECT array_agg(id) INTO v_purch_products FROM products WHERE organization_id = v_org_id AND item_type = 'SERVICE' AND sku LIKE 'SVC-P-%';

    SELECT COALESCE(MAX(NULLIF(regexp_replace(entry_number, '.*/', ''), '')::bigint), 0)
    INTO v_je_seq
    FROM journal_entries
    WHERE organization_id = v_org_id AND entry_number LIKE 'JV/' || v_fy || '/%';

    SELECT COALESCE(MAX(NULLIF(regexp_replace(payment_number, '.*/', ''), '')::bigint), 0)
    INTO v_pay_seq
    FROM payments
    WHERE organization_id = v_org_id AND payment_number LIKE 'PAY/' || v_fy || '/%';

    v_last_month := date_trunc('month', v_today)::DATE;
    v_months := (EXTRACT(YEAR FROM AGE(v_last_month, DATE '2026-04-01')) * 12
        + EXTRACT(MONTH FROM AGE(v_last_month, DATE '2026-04-01')))::INT;

    FOR m IN 0..v_months LOOP
        v_month_start := (DATE '2026-04-01' + (m || ' months')::INTERVAL)::DATE;
        v_month_end := (v_month_start + INTERVAL '1 month' - INTERVAL '1 day')::DATE;
        IF v_month_end > v_today THEN
            v_month_end := v_today;
        END IF;

        SELECT id INTO v_period_id
        FROM accounting_periods
        WHERE organization_id = v_org_id AND start_date = v_month_start
        LIMIT 1;
        IF v_period_id IS NULL THEN
            RAISE EXCEPTION 'V24: missing accounting period for %', v_month_start;
        END IF;

        FOR v_inv_idx IN 1..12 LOOP
            v_si_seq := v_si_seq + 1;
            v_taxable := 208333.33;
            v_cgst := round(v_taxable * 0.09, 2);
            v_sgst := round(v_taxable * 0.09, 2);
            v_grand := v_taxable + v_cgst + v_sgst;
            v_inv_date := LEAST(v_month_start + LEAST(v_inv_idx + 1, GREATEST(v_month_end - v_month_start, 0)), v_month_end);
            v_cust := v_customer_ids[1 + ((v_inv_idx + m) % array_length(v_customer_ids, 1))];
            v_prod := v_sales_products[1 + ((v_inv_idx - 1) % array_length(v_sales_products, 1))];
            v_si_id := gen_random_uuid();
            v_inv_number := 'INV/' || v_fy || '/' || lpad(v_si_seq::text, 6, '0');

            INSERT INTO sales_invoices (
                id, organization_id, invoice_number, invoice_date, due_date, customer_id, warehouse_id,
                place_of_supply, status, payment_status,
                subtotal, taxable_amount, cgst_total, sgst_total, igst_total,
                grand_total, amount_paid, outstanding_amount,
                accounting_status, accounting_posted_at, created_by
            ) VALUES (
                v_si_id, v_org_id, v_inv_number, v_inv_date, v_inv_date + 30, v_cust, v_wh_id,
                'Maharashtra', 'CONFIRMED', 'UNPAID',
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
                v_si_id, v_prod, 'Professional services', 1, v_unit_id, v_taxable,
                18, v_taxable, 9, 9, 0, v_cgst, v_sgst, 0, v_grand, 1,
                'GST', 'PLACE_OF_SUPPLY'
            );

            v_je_seq := v_je_seq + 1;
            v_je_id := gen_random_uuid();
            v_entry_number := 'JV/' || v_fy || '/' || lpad(v_je_seq::text, 6, '0');
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
                (v_org_id, v_je_id, v_acc_ar, 1, 'AR', v_grand, 0, v_cust, v_user_id),
                (v_org_id, v_je_id, v_acc_sales, 2, 'Sales', 0, v_taxable, v_cust, v_user_id),
                (v_org_id, v_je_id, v_acc_ocgst, 3, 'Output CGST', 0, v_cgst, NULL, v_user_id),
                (v_org_id, v_je_id, v_acc_osgst, 4, 'Output SGST', 0, v_sgst, NULL, v_user_id);
            UPDATE sales_invoices SET posted_journal_entry_id = v_je_id WHERE id = v_si_id;

            IF (v_inv_idx % 4) <> 0 THEN
                v_paid := round(v_grand * 0.75, 2);
                v_pay_seq := v_pay_seq + 1;
                v_pay_id := gen_random_uuid();
                INSERT INTO payments (
                    id, organization_id, payment_number, payment_date, payment_type, party_type,
                    customer_id, amount, payment_mode, notes,
                    accounting_status, accounting_posted_at, created_by
                ) VALUES (
                    v_pay_id, v_org_id, 'PAY/' || v_fy || '/' || lpad(v_pay_seq::text, 6, '0'),
                    LEAST(v_inv_date + 7, v_today), 'RECEIPT', 'CUSTOMER',
                    v_cust, v_paid, 'BANK_TRANSFER', 'FY26-27 seed receipt',
                    'POSTED', NOW(), v_user_id
                );
                INSERT INTO payment_allocations (payment_id, document_type, document_id, allocated_amount)
                VALUES (v_pay_id, 'SALES_INVOICE', v_si_id, v_paid);
                UPDATE sales_invoices SET amount_paid = v_paid, outstanding_amount = v_grand - v_paid, payment_status = 'PARTIALLY_PAID'
                WHERE id = v_si_id;

                v_je_seq := v_je_seq + 1;
                v_je_id := gen_random_uuid();
                INSERT INTO journal_entries (
                    id, organization_id, fiscal_year_id, accounting_period_id,
                    entry_number, entry_date, posting_date, reference_type, reference_id,
                    voucher_type, description, status, source,
                    total_debit, total_credit, posted_at, posted_by, created_by
                ) VALUES (
                    v_je_id, v_org_id, v_fy_id, v_period_id,
                    'JV/' || v_fy || '/' || lpad(v_je_seq::text, 6, '0'),
                    LEAST(v_inv_date + 7, v_today), LEAST(v_inv_date + 7, v_today),
                    'PAYMENT', v_pay_id, 'RECEIPT', 'Customer receipt', 'POSTED', 'CUSTOMER_RECEIPT',
                    v_paid, v_paid, NOW(), v_user_id, v_user_id
                );
                INSERT INTO journal_entry_lines (
                    organization_id, journal_entry_id, account_id, line_number, description,
                    debit_amount, credit_amount, customer_id, created_by
                ) VALUES
                    (v_org_id, v_je_id, v_acc_bank, 1, 'Bank', v_paid, 0, v_cust, v_user_id),
                    (v_org_id, v_je_id, v_acc_ar, 2, 'AR', 0, v_paid, v_cust, v_user_id);
                UPDATE payments SET posted_journal_entry_id = v_je_id WHERE id = v_pay_id;
            END IF;
        END LOOP;

        FOR v_inv_idx IN 1..10 LOOP
            v_pi_seq := v_pi_seq + 1;
            v_taxable := 150000.00;
            v_cgst := round(v_taxable * 0.09, 2);
            v_sgst := round(v_taxable * 0.09, 2);
            v_grand := v_taxable + v_cgst + v_sgst;
            v_inv_date := LEAST(v_month_start + LEAST(v_inv_idx + 3, GREATEST(v_month_end - v_month_start, 0)), v_month_end);
            v_supp := v_supplier_ids[1 + ((v_inv_idx + m) % array_length(v_supplier_ids, 1))];
            v_prod := v_purch_products[1 + ((v_inv_idx - 1) % array_length(v_purch_products, 1))];
            v_pi_id := gen_random_uuid();
            v_inv_number := 'PINV/' || v_fy || '/' || lpad(v_pi_seq::text, 6, '0');

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
                v_grand, 0, v_grand, 'POSTED', NOW(), v_user_id
            );
            INSERT INTO purchase_invoice_items (
                purchase_invoice_id, product_id, description, quantity, unit_id, rate,
                tax_rate, taxable_amount, cgst_rate, sgst_rate, igst_rate,
                cgst_amount, sgst_amount, igst_amount, line_total, line_order,
                tax_type, split_strategy
            ) VALUES (
                v_pi_id, v_prod, 'Purchased services', 1, v_unit_id, v_taxable,
                18, v_taxable, 9, 9, 0, v_cgst, v_sgst, 0, v_grand, 1,
                'GST', 'PLACE_OF_SUPPLY'
            );

            v_je_seq := v_je_seq + 1;
            v_je_id := gen_random_uuid();
            INSERT INTO journal_entries (
                id, organization_id, fiscal_year_id, accounting_period_id,
                entry_number, entry_date, posting_date, reference_type, reference_id,
                voucher_type, voucher_number, description, status, source,
                total_debit, total_credit, posted_at, posted_by, created_by
            ) VALUES (
                v_je_id, v_org_id, v_fy_id, v_period_id,
                'JV/' || v_fy || '/' || lpad(v_je_seq::text, 6, '0'),
                v_inv_date, v_inv_date, 'PURCHASE_INVOICE', v_pi_id,
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
        END LOOP;
    END LOOP;

    UPDATE document_sequences SET next_value = GREATEST(next_value, v_si_seq + 1)
    WHERE organization_id = v_org_id AND document_type = 'SALES_INVOICE' AND financial_year = v_fy;
    UPDATE document_sequences SET next_value = GREATEST(next_value, v_pi_seq + 1)
    WHERE organization_id = v_org_id AND document_type = 'PURCHASE_INVOICE' AND financial_year = v_fy;
    UPDATE document_sequences SET next_value = GREATEST(next_value, v_je_seq + 1)
    WHERE organization_id = v_org_id AND document_type = 'JOURNAL' AND financial_year = v_fy;

    RAISE NOTICE 'V24 FY 2026-27 seed complete for YRV (months 0..%)', v_months;
END
$yrv24$;
