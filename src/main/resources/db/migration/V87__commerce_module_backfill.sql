-- Enable Commerce module for existing tenants with Retail (COMMERCE depends on RETAIL + INVENTORY).

INSERT INTO edition_modules (edition_code, module_code)
VALUES ('PROFESSIONAL', 'COMMERCE'), ('ENTERPRISE', 'COMMERCE')
ON CONFLICT DO NOTHING;

-- Provision COMMERCE for orgs that already have Retail enabled.
INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
SELECT om.organization_id, 'COMMERCE', TRUE, TRUE, FALSE, '{}'::jsonb
FROM organization_modules om
WHERE om.module_code = 'RETAIL'
  AND om.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM organization_modules x
      WHERE x.organization_id = om.organization_id AND x.module_code = 'COMMERCE'
  );

-- Orgs with retail_enabled in settings but no module row yet.
INSERT INTO organization_modules (organization_id, module_code, enabled, licensed, trial, configuration)
SELECT os.organization_id, 'COMMERCE', os.retail_enabled, TRUE, FALSE, '{}'::jsonb
FROM organization_settings os
WHERE os.retail_enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM organization_modules x
      WHERE x.organization_id = os.organization_id AND x.module_code = 'COMMERCE'
  );

-- Default-enable COMMERCE where a row exists but was never toggled on (legacy gap after V78).
UPDATE organization_modules om
SET enabled = TRUE,
    updated_at = NOW()
FROM organization_modules retail
WHERE om.module_code = 'COMMERCE'
  AND om.enabled = FALSE
  AND retail.organization_id = om.organization_id
  AND retail.module_code = 'RETAIL'
  AND retail.enabled = TRUE;

-- Feature rows for provisioned COMMERCE modules.
INSERT INTO organization_features (organization_id, module_code, feature_code, enabled, licensed, trial, configuration)
SELECT om.organization_id, mf.module_code, mf.feature_code, mf.enabled_by_default, TRUE, FALSE, '{}'::jsonb
FROM organization_modules om
JOIN module_features mf ON mf.module_code = om.module_code
WHERE om.module_code = 'COMMERCE'
  AND om.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM organization_features of2
      WHERE of2.organization_id = om.organization_id
        AND of2.module_code = mf.module_code
        AND of2.feature_code = mf.feature_code
  );
