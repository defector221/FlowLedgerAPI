-- Backfill merchant onboarding for orgs with Commerce enabled via module backfill (V87 gap).

INSERT INTO merchant_integration_profiles (
    id, organization_id, merchant_type, integration_type, connector_type, status, health_status, created_at, updated_at)
SELECT gen_random_uuid(), om.organization_id, 'FLOWLEDGER', 'FLOWLEDGER', 'FLOWLEDGER', 'ACTIVE', 'HEALTHY', NOW(), NOW()
FROM organization_modules om
WHERE om.module_code = 'COMMERCE'
  AND om.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM merchant_integration_profiles mip WHERE mip.organization_id = om.organization_id);

INSERT INTO merchant_capability_profiles (id, organization_id, supports_marketplace, created_at, updated_at)
SELECT gen_random_uuid(), om.organization_id, TRUE, NOW(), NOW()
FROM organization_modules om
WHERE om.module_code = 'COMMERCE'
  AND om.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM merchant_capability_profiles mcp WHERE mcp.organization_id = om.organization_id);

INSERT INTO merchant_onboarding (id, organization_id, state, state_changed_at, created_at, updated_at)
SELECT gen_random_uuid(), om.organization_id, 'LIVE', NOW(), NOW(), NOW()
FROM organization_modules om
WHERE om.module_code = 'COMMERCE'
  AND om.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM merchant_onboarding mo WHERE mo.organization_id = om.organization_id);
