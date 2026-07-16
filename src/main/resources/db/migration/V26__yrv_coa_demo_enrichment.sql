-- YRV Solutions: enrich Chart of Accounts for COA UI demo (descriptions, custom accounts, opex journals).
-- Runs after V25. Idempotent.

DO $yrv26$
DECLARE
    v_org_id UUID;
    v_user_id UUID;
    v_fy_id UUID;
    v_period_id UUID;
    v_grp_expense UUID;
    v_grp_revenue UUID;
    v_acc_bank UUID;
    v_acc_salaries UUID;
    v_acc_rent UUID;
    v_acc_interest UUID;
    v_je_id UUID;
    v_je_seq BIGINT;
    v_month_start DATE;
    v_month_end DATE;
    v_entry_date DATE;
    v_entry_number TEXT;
    v_fy TEXT := '2026-27';
    m INT;
BEGIN
    SELECT o.id INTO v_org_id FROM organizations o WHERE o.name = 'YRV Solutions' ORDER BY o.created_at DESC LIMIT 1;
    IF v_org_id IS NULL THEN
        RAISE NOTICE 'V26 skipped: YRV Solutions not found';
        RETURN;
    END IF;

    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.organization_id = v_org_id OR u.last_active_organization_id = v_org_id
    ORDER BY CASE WHEN lower(u.email) = lower('kashyap221@gmail.com') THEN 0 ELSE 1 END
    LIMIT 1;

    -- System account descriptions (safe to re-run).
    UPDATE accounts SET description = 'Petty cash and cash on hand'
    WHERE organization_id = v_org_id AND account_code = '1000' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Primary business bank account'
    WHERE organization_id = v_org_id AND account_code = '1010' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Trade debtors from confirmed sales invoices'
    WHERE organization_id = v_org_id AND account_code = '1100' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Valuation of physical goods in warehouses'
    WHERE organization_id = v_org_id AND account_code = '1200' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Input CGST credit on purchases'
    WHERE organization_id = v_org_id AND account_code = '1300' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Input SGST credit on purchases'
    WHERE organization_id = v_org_id AND account_code = '1310' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Input IGST credit on interstate purchases'
    WHERE organization_id = v_org_id AND account_code = '1320' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Trade creditors from purchase invoices'
    WHERE organization_id = v_org_id AND account_code = '2000' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Output CGST liability on sales'
    WHERE organization_id = v_org_id AND account_code = '2100' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Output SGST liability on sales'
    WHERE organization_id = v_org_id AND account_code = '2110' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Output IGST liability on interstate sales'
    WHERE organization_id = v_org_id AND account_code = '2120' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Owner / shareholder capital'
    WHERE organization_id = v_org_id AND account_code = '3000' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Accumulated profits retained in the business'
    WHERE organization_id = v_org_id AND account_code = '3100' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Revenue from sales of goods and services'
    WHERE organization_id = v_org_id AND account_code = '4000' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Miscellaneous operating income'
    WHERE organization_id = v_org_id AND account_code = '4100' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Discounts received from suppliers'
    WHERE organization_id = v_org_id AND account_code = '4200' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Rounding gains on transactions'
    WHERE organization_id = v_org_id AND account_code = '4300' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Cost of goods and services purchased'
    WHERE organization_id = v_org_id AND account_code = '5000' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Direct cost of inventory sold'
    WHERE organization_id = v_org_id AND account_code = '5100' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'General operating expenses (system bucket)'
    WHERE organization_id = v_org_id AND account_code = '5200' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Trade discounts allowed to customers'
    WHERE organization_id = v_org_id AND account_code = '5300' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Rounding losses on transactions'
    WHERE organization_id = v_org_id AND account_code = '5400' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Top-level asset accounts'
    WHERE organization_id = v_org_id AND account_code = 'GRP-ASSET' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Top-level liability accounts'
    WHERE organization_id = v_org_id AND account_code = 'GRP-LIABILITY' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Owner equity and retained earnings'
    WHERE organization_id = v_org_id AND account_code = 'GRP-EQUITY' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Revenue and other income'
    WHERE organization_id = v_org_id AND account_code = 'GRP-REVENUE' AND (description IS NULL OR description = '');

    UPDATE accounts SET description = 'Operating and cost-of-sales expenses'
    WHERE organization_id = v_org_id AND account_code = 'GRP-EXPENSE' AND (description IS NULL OR description = '');

    SELECT id INTO v_grp_expense FROM accounts WHERE organization_id = v_org_id AND account_code = 'GRP-EXPENSE' LIMIT 1;
    SELECT id INTO v_grp_revenue FROM accounts WHERE organization_id = v_org_id AND account_code = 'GRP-REVENUE' LIMIT 1;
    SELECT id INTO v_acc_bank FROM accounts WHERE organization_id = v_org_id AND system_account_key = 'BANK' LIMIT 1;

    IF v_grp_expense IS NULL OR v_acc_bank IS NULL THEN
        RAISE NOTICE 'V26 skipped: COA hierarchy not ready for YRV (run V25 first)';
        RETURN;
    END IF;

    -- Custom org accounts (editable in COA tree UI).
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE organization_id = v_org_id AND account_code = '5250') THEN
        v_acc_salaries := gen_random_uuid();
        INSERT INTO accounts (
            id, organization_id, account_code, account_name, account_type, account_sub_type,
            parent_account_id, system_account, active, allow_manual_posting,
            is_editable, is_deletable, status, description,
            opening_debit, opening_credit
        ) VALUES (
            v_acc_salaries, v_org_id, '5250', 'Salaries & Wages', 'EXPENSE', 'INDIRECT_EXPENSE',
            v_grp_expense, FALSE, TRUE, TRUE,
            TRUE, TRUE, 'ACTIVE', 'Monthly payroll for YRV team',
            0, 0
        );
    ELSE
        SELECT id INTO v_acc_salaries FROM accounts WHERE organization_id = v_org_id AND account_code = '5250';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM accounts WHERE organization_id = v_org_id AND account_code = '5255') THEN
        v_acc_rent := gen_random_uuid();
        INSERT INTO accounts (
            id, organization_id, account_code, account_name, account_type, account_sub_type,
            parent_account_id, system_account, active, allow_manual_posting,
            is_editable, is_deletable, status, description,
            opening_debit, opening_credit
        ) VALUES (
            v_acc_rent, v_org_id, '5255', 'Rent & Utilities', 'EXPENSE', 'INDIRECT_EXPENSE',
            v_grp_expense, FALSE, TRUE, TRUE,
            TRUE, TRUE, 'ACTIVE', 'Mumbai office rent and utilities',
            0, 0
        );
    ELSE
        SELECT id INTO v_acc_rent FROM accounts WHERE organization_id = v_org_id AND account_code = '5255';
    END IF;

    IF v_grp_revenue IS NOT NULL AND NOT EXISTS (SELECT 1 FROM accounts WHERE organization_id = v_org_id AND account_code = '4155') THEN
        v_acc_interest := gen_random_uuid();
        INSERT INTO accounts (
            id, organization_id, account_code, account_name, account_type, account_sub_type,
            parent_account_id, system_account, active, allow_manual_posting,
            is_editable, is_deletable, status, description,
            opening_debit, opening_credit
        ) VALUES (
            v_acc_interest, v_org_id, '4155', 'Bank Interest', 'REVENUE', 'INDIRECT_INCOME',
            v_grp_revenue, FALSE, TRUE, TRUE,
            TRUE, TRUE, 'ACTIVE', 'Interest earned on bank balances',
            0, 0
        );
    END IF;

    IF EXISTS (
        SELECT 1 FROM journal_entries
        WHERE organization_id = v_org_id AND description = 'YRV demo monthly opex'
    ) THEN
        RAISE NOTICE 'V26 YRV COA descriptions + custom accounts applied; opex journals already present';
        RETURN;
    END IF;

    IF CURRENT_DATE < DATE '2026-04-01' THEN
        RAISE NOTICE 'V26 custom accounts applied; opex journals skipped (before FY 2026-27)';
        RETURN;
    END IF;

    SELECT id INTO v_fy_id FROM fiscal_years WHERE organization_id = v_org_id AND name = v_fy LIMIT 1;
    IF v_fy_id IS NULL THEN
        RAISE NOTICE 'V26 opex journals skipped: FY 2026-27 missing';
        RETURN;
    END IF;

    SELECT COALESCE(MAX(NULLIF(regexp_replace(entry_number, '.*/', ''), '')::bigint), 0)
    INTO v_je_seq
    FROM journal_entries
    WHERE organization_id = v_org_id AND entry_number LIKE 'JV/' || v_fy || '/%';

    FOR m IN 0..LEAST(
        (EXTRACT(YEAR FROM AGE(date_trunc('month', CURRENT_DATE)::DATE, DATE '2026-04-01')) * 12
            + EXTRACT(MONTH FROM AGE(date_trunc('month', CURRENT_DATE)::DATE, DATE '2026-04-01')))::INT,
        11
    ) LOOP
        v_month_start := (DATE '2026-04-01' + (m || ' months')::INTERVAL)::DATE;
        v_month_end := (v_month_start + INTERVAL '1 month' - INTERVAL '1 day')::DATE;
        v_entry_date := v_month_start + 5;

        SELECT id INTO v_period_id
        FROM accounting_periods
        WHERE organization_id = v_org_id AND start_date = v_month_start
        LIMIT 1;

        IF v_period_id IS NULL THEN
            CONTINUE;
        END IF;

        -- Salaries
        v_je_seq := v_je_seq + 1;
        v_je_id := gen_random_uuid();
        v_entry_number := 'JV/' || v_fy || '/' || lpad(v_je_seq::text, 6, '0');
        INSERT INTO journal_entries (
            id, organization_id, fiscal_year_id, accounting_period_id,
            entry_number, entry_date, posting_date, reference_type,
            voucher_type, voucher_number, description, status, source,
            total_debit, total_credit, posted_at, posted_by, created_by
        ) VALUES (
            v_je_id, v_org_id, v_fy_id, v_period_id,
            v_entry_number, v_entry_date, v_entry_date, 'JOURNAL_ENTRY',
            'JOURNAL', 'OPEX/SAL/' || to_char(v_month_start, 'YYYYMM'),
            'YRV demo monthly opex', 'POSTED', 'MANUAL',
            125000.00, 125000.00, NOW(), v_user_id, v_user_id
        );
        INSERT INTO journal_entry_lines (
            organization_id, journal_entry_id, account_id, line_number, description,
            debit_amount, credit_amount, created_by
        ) VALUES
            (v_org_id, v_je_id, v_acc_salaries, 1, 'Payroll ' || to_char(v_month_start, 'Mon YYYY'), 125000.00, 0, v_user_id),
            (v_org_id, v_je_id, v_acc_bank, 2, 'Bank payment', 0, 125000.00, v_user_id);

        -- Rent
        v_je_seq := v_je_seq + 1;
        v_je_id := gen_random_uuid();
        v_entry_number := 'JV/' || v_fy || '/' || lpad(v_je_seq::text, 6, '0');
        INSERT INTO journal_entries (
            id, organization_id, fiscal_year_id, accounting_period_id,
            entry_number, entry_date, posting_date, reference_type,
            voucher_type, voucher_number, description, status, source,
            total_debit, total_credit, posted_at, posted_by, created_by
        ) VALUES (
            v_je_id, v_org_id, v_fy_id, v_period_id,
            v_entry_number, v_entry_date + 2, v_entry_date + 2, 'JOURNAL_ENTRY',
            'JOURNAL', 'OPEX/RENT/' || to_char(v_month_start, 'YYYYMM'),
            'YRV demo monthly opex', 'POSTED', 'MANUAL',
            42000.00, 42000.00, NOW(), v_user_id, v_user_id
        );
        INSERT INTO journal_entry_lines (
            organization_id, journal_entry_id, account_id, line_number, description,
            debit_amount, credit_amount, created_by
        ) VALUES
            (v_org_id, v_je_id, v_acc_rent, 1, 'Rent ' || to_char(v_month_start, 'Mon YYYY'), 42000.00, 0, v_user_id),
            (v_org_id, v_je_id, v_acc_bank, 2, 'Bank payment', 0, 42000.00, v_user_id);
    END LOOP;

    RAISE NOTICE 'V26 YRV COA enrichment complete (descriptions, custom accounts, opex journals)';
END
$yrv26$;
