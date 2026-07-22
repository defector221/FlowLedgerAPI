-- Seed one advisory AI approval workflow for the YRV Solutions demo org (idempotent).

DO $$
DECLARE
  v_org_id UUID;
BEGIN
  SELECT o.id INTO v_org_id
  FROM organizations o
  WHERE o.name = 'YRV Solutions'
  ORDER BY o.created_at DESC
  LIMIT 1;

  IF v_org_id IS NULL THEN
    RAISE NOTICE 'V36 skipped: organization YRV Solutions not found';
    RETURN;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ai_workflow_drafts d
    WHERE d.organization_id = v_org_id
      AND d.name = 'Seed: Sales document approval'
  ) THEN
    RAISE NOTICE 'V36 skipped: seed workflow already exists';
    RETURN;
  END IF;

  INSERT INTO ai_workflow_drafts (
    id,
    organization_id,
    created_by,
    name,
    trigger_type,
    description,
    conditions_json,
    steps_json,
    suggested_approvers,
    status,
    created_at,
    updated_at
  ) VALUES (
    gen_random_uuid(),
    v_org_id,
    NULL,
    'Seed: Sales document approval',
    'PAYMENT_OR_INVOICE',
    'Demo advisory workflow: submit quotation/sales order/invoice for admin approval, then accountant review for amounts over ₹50,000. Activation stores config only — it does not auto-approve ERP documents.',
    '{"source":"flyway-seed","minAmount":50000,"documentTypes":["QUOTATION","SALES_ORDER","SALES_INVOICE"]}',
    '[{"order":1,"role":"REQUESTER","action":"SUBMIT"},{"order":2,"role":"ORGANIZATION_ADMIN","action":"APPROVE"},{"order":3,"role":"ACCOUNTANT","action":"REVIEW"}]',
    'ORGANIZATION_ADMIN,ACCOUNTANT',
    'ACTIVE',
    NOW(),
    NOW()
  );

  RAISE NOTICE 'V36 seeded AI approval workflow for YRV Solutions';
END $$;
