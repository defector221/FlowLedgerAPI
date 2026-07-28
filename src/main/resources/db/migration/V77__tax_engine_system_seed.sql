-- System tax category templates are org-scoped; demo/new orgs seed via TaxSetupGenerator.
-- This migration adds reference jurisdiction rows for orgs that opt-in via tax setup only.
-- No global rows without organization_id (multi-tenant).

COMMENT ON TABLE tax_categories IS 'Org-scoped tax category master (GST_5, GST_18, EXEMPT, etc.)';
COMMENT ON TABLE tax_rules IS 'Immutable versioned tax rates; never UPDATE percentages — close and INSERT new version';
