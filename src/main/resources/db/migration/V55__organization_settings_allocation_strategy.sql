ALTER TABLE organization_settings
    ADD COLUMN IF NOT EXISTS allocation_strategy VARCHAR(32) NOT NULL DEFAULT 'FIFO';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_org_settings_allocation_strategy'
    ) THEN
        ALTER TABLE organization_settings
            ADD CONSTRAINT chk_org_settings_allocation_strategy CHECK (
                allocation_strategy IN (
                    'DEFAULT', 'FIFO', 'FEFO', 'LIFO', 'HIGHEST_QUANTITY', 'PREFERRED_WAREHOUSE'
                )
            );
    END IF;
END $$;
