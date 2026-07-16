-- Chart of Accounts: metadata flags, status, description, hierarchy group headers.

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_editable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS is_deletable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE INDEX IF NOT EXISTS idx_accounts_org_status ON accounts (organization_id, status);

-- Backfill status from legacy active flag.
UPDATE accounts SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;

-- System accounts: not deletable; editable for name/description/status only.
UPDATE accounts
SET is_editable = TRUE,
    is_deletable = FALSE
WHERE system_account = TRUE;

-- User-created accounts remain fully editable/deletable unless restricted later.
UPDATE accounts
SET is_editable = TRUE,
    is_deletable = TRUE
WHERE system_account = FALSE;

-- Add type group headers and wire existing leaf accounts under them (idempotent per org).
DO $coa_hierarchy$
DECLARE
    r RECORD;
    v_asset UUID;
    v_liab UUID;
    v_equity UUID;
    v_revenue UUID;
    v_expense UUID;
BEGIN
    FOR r IN SELECT DISTINCT organization_id AS org_id FROM accounts LOOP
        SELECT id INTO v_asset
        FROM accounts
        WHERE organization_id = r.org_id AND account_code = 'GRP-ASSET'
        LIMIT 1;
        IF v_asset IS NULL THEN
            v_asset := gen_random_uuid();
            INSERT INTO accounts (
                id, organization_id, account_code, account_name, account_type,
                system_account, active, allow_manual_posting, is_editable, is_deletable, status,
                opening_debit, opening_credit
            ) VALUES (
                v_asset, r.org_id, 'GRP-ASSET', 'Assets', 'ASSET',
                TRUE, TRUE, FALSE, FALSE, FALSE, 'ACTIVE', 0, 0
            );
        END IF;

        SELECT id INTO v_liab
        FROM accounts
        WHERE organization_id = r.org_id AND account_code = 'GRP-LIABILITY'
        LIMIT 1;
        IF v_liab IS NULL THEN
            v_liab := gen_random_uuid();
            INSERT INTO accounts (
                id, organization_id, account_code, account_name, account_type,
                system_account, active, allow_manual_posting, is_editable, is_deletable, status,
                opening_debit, opening_credit
            ) VALUES (
                v_liab, r.org_id, 'GRP-LIABILITY', 'Liabilities', 'LIABILITY',
                TRUE, TRUE, FALSE, FALSE, FALSE, 'ACTIVE', 0, 0
            );
        END IF;

        SELECT id INTO v_equity
        FROM accounts
        WHERE organization_id = r.org_id AND account_code = 'GRP-EQUITY'
        LIMIT 1;
        IF v_equity IS NULL THEN
            v_equity := gen_random_uuid();
            INSERT INTO accounts (
                id, organization_id, account_code, account_name, account_type,
                system_account, active, allow_manual_posting, is_editable, is_deletable, status,
                opening_debit, opening_credit
            ) VALUES (
                v_equity, r.org_id, 'GRP-EQUITY', 'Equity', 'EQUITY',
                TRUE, TRUE, FALSE, FALSE, FALSE, 'ACTIVE', 0, 0
            );
        END IF;

        SELECT id INTO v_revenue
        FROM accounts
        WHERE organization_id = r.org_id AND account_code = 'GRP-REVENUE'
        LIMIT 1;
        IF v_revenue IS NULL THEN
            v_revenue := gen_random_uuid();
            INSERT INTO accounts (
                id, organization_id, account_code, account_name, account_type,
                system_account, active, allow_manual_posting, is_editable, is_deletable, status,
                opening_debit, opening_credit
            ) VALUES (
                v_revenue, r.org_id, 'GRP-REVENUE', 'Income', 'REVENUE',
                TRUE, TRUE, FALSE, FALSE, FALSE, 'ACTIVE', 0, 0
            );
        END IF;

        SELECT id INTO v_expense
        FROM accounts
        WHERE organization_id = r.org_id AND account_code = 'GRP-EXPENSE'
        LIMIT 1;
        IF v_expense IS NULL THEN
            v_expense := gen_random_uuid();
            INSERT INTO accounts (
                id, organization_id, account_code, account_name, account_type,
                system_account, active, allow_manual_posting, is_editable, is_deletable, status,
                opening_debit, opening_credit
            ) VALUES (
                v_expense, r.org_id, 'GRP-EXPENSE', 'Expense', 'EXPENSE',
                TRUE, TRUE, FALSE, FALSE, FALSE, 'ACTIVE', 0, 0
            );
        END IF;

        UPDATE accounts SET parent_account_id = v_asset
        WHERE organization_id = r.org_id
          AND account_type = 'ASSET'
          AND account_code NOT LIKE 'GRP-%'
          AND (parent_account_id IS NULL OR parent_account_id <> v_asset);

        UPDATE accounts SET parent_account_id = v_liab
        WHERE organization_id = r.org_id
          AND account_type = 'LIABILITY'
          AND account_code NOT LIKE 'GRP-%'
          AND (parent_account_id IS NULL OR parent_account_id <> v_liab);

        UPDATE accounts SET parent_account_id = v_equity
        WHERE organization_id = r.org_id
          AND account_type = 'EQUITY'
          AND account_code NOT LIKE 'GRP-%'
          AND (parent_account_id IS NULL OR parent_account_id <> v_equity);

        UPDATE accounts SET parent_account_id = v_revenue
        WHERE organization_id = r.org_id
          AND account_type = 'REVENUE'
          AND account_code NOT LIKE 'GRP-%'
          AND (parent_account_id IS NULL OR parent_account_id <> v_revenue);

        UPDATE accounts SET parent_account_id = v_expense
        WHERE organization_id = r.org_id
          AND account_type = 'EXPENSE'
          AND account_code NOT LIKE 'GRP-%'
          AND (parent_account_id IS NULL OR parent_account_id <> v_expense);
    END LOOP;
END
$coa_hierarchy$;
