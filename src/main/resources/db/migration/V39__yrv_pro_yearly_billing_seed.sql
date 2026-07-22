-- YRV Solutions: Pro (BUSINESS) YEARLY subscription + billing invoice seed (idempotent).
-- Also refreshes Pro plan copy/pricing for demo.

UPDATE subscription_plans
SET name = 'Pro',
    description = 'Pro plan for growing teams — AI workflows, higher limits, yearly billing available',
    price_monthly = 1999,
    price_yearly = 19990,
    max_organizations = 5,
    max_users_per_org = 25,
    max_invoices_per_month = 2000,
    display_order = 3,
    highlight_plan = TRUE,
    recommended = FALSE,
    currency = 'INR',
    features_json = COALESCE(features_json, '{}'::jsonb) || '{"reminders":true,"marketing":true,"crm":true,"ai":true}'::jsonb,
    updated_at = NOW()
WHERE code = 'BUSINESS';

UPDATE subscription_plans
SET recommended = TRUE,
    display_order = 2,
    updated_at = NOW()
WHERE code = 'STARTER';

DO $$
DECLARE
  v_org_id UUID;
  v_plan_id UUID;
  v_admin_id UUID;
  v_txn_id UUID;
  v_invoice_id UUID;
  v_amount NUMERIC(19,4) := 19990;
  v_start TIMESTAMPTZ := date_trunc('day', NOW());
  v_next TIMESTAMPTZ := date_trunc('day', NOW()) + INTERVAL '1 year';
BEGIN
  SELECT o.id INTO v_org_id
  FROM organizations o
  WHERE o.name = 'YRV Solutions'
  ORDER BY o.created_at DESC
  LIMIT 1;

  IF v_org_id IS NULL THEN
    RAISE NOTICE 'V39 skipped: organization YRV Solutions not found';
    RETURN;
  END IF;

  SELECT id INTO v_plan_id FROM subscription_plans WHERE code = 'BUSINESS' LIMIT 1;
  IF v_plan_id IS NULL THEN
    RAISE NOTICE 'V39 skipped: BUSINESS/Pro plan not found';
    RETURN;
  END IF;

  SELECT om.user_id INTO v_admin_id
  FROM organization_memberships om
  JOIN organization_membership_roles omr ON omr.membership_id = om.id
  JOIN roles r ON r.id = omr.role_id AND r.code = 'ORGANIZATION_ADMIN'
  WHERE om.organization_id = v_org_id
    AND om.status = 'ACTIVE'
  ORDER BY om.created_at ASC
  LIMIT 1;

  -- Org subscription → Pro YEARLY
  INSERT INTO organization_subscriptions (
    id, organization_id, plan_id, billing_cycle, status,
    start_date, end_date, next_billing_date, auto_renew,
    payment_provider, payment_reference, created_at, updated_at
  )
  VALUES (
    gen_random_uuid(), v_org_id, v_plan_id, 'YEARLY', 'ACTIVE',
    v_start, NULL, v_next, TRUE,
    'seed', 'yrv-pro-yearly-demo', NOW(), NOW()
  )
  ON CONFLICT (organization_id) DO UPDATE
  SET plan_id = EXCLUDED.plan_id,
      billing_cycle = 'YEARLY',
      status = 'ACTIVE',
      start_date = COALESCE(organization_subscriptions.start_date, EXCLUDED.start_date),
      end_date = NULL,
      next_billing_date = EXCLUDED.next_billing_date,
      auto_renew = TRUE,
      payment_provider = COALESCE(organization_subscriptions.payment_provider, 'seed'),
      payment_reference = COALESCE(organization_subscriptions.payment_reference, 'yrv-pro-yearly-demo'),
      updated_at = NOW();

  -- Admin user subscription mirrors org plan
  IF v_admin_id IS NOT NULL THEN
    INSERT INTO user_subscriptions (id, user_id, plan_id, status, starts_at, created_at, updated_at)
    VALUES (gen_random_uuid(), v_admin_id, v_plan_id, 'ACTIVE', v_start, NOW(), NOW())
    ON CONFLICT (user_id) DO UPDATE
    SET plan_id = EXCLUDED.plan_id,
        status = 'ACTIVE',
        updated_at = NOW();
  END IF;

  -- Seed paid yearly checkout transaction + invoice if missing
  IF NOT EXISTS (
    SELECT 1 FROM payment_transactions
    WHERE organization_id = v_org_id
      AND provider_order_id = 'seed_yrv_pro_yearly'
  ) THEN
    v_txn_id := gen_random_uuid();
    INSERT INTO payment_transactions (
      id, organization_id, provider, provider_order_id, payment_id,
      amount, currency, status, purpose, plan_id, billing_cycle,
      raw_response, created_at, updated_at
    ) VALUES (
      v_txn_id, v_org_id, 'seed', 'seed_yrv_pro_yearly', 'pay_seed_yrv_pro',
      v_amount, 'INR', 'PAID', 'CHECKOUT', v_plan_id, 'YEARLY',
      '{"source":"flyway-v39","plan":"BUSINESS","cycle":"YEARLY"}'::jsonb,
      NOW(), NOW()
    );

    IF NOT EXISTS (
      SELECT 1 FROM subscription_invoices
      WHERE organization_id = v_org_id
        AND invoice_number = 'SUB/YRV/PRO-YEARLY-001'
    ) THEN
      v_invoice_id := gen_random_uuid();
      INSERT INTO subscription_invoices (
        id, organization_id, invoice_number, amount, gst, discount, total,
        paid_at, payment_transaction_id, created_at, updated_at
      ) VALUES (
        v_invoice_id, v_org_id, 'SUB/YRV/PRO-YEARLY-001',
        v_amount, 0, 0, v_amount,
        NOW(), v_txn_id, NOW(), NOW()
      );
    END IF;
  END IF;

  RAISE NOTICE 'V39 seeded YRV Solutions on Pro YEARLY with billing invoice';
END $$;
