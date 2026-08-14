-- Local shells only: link FlowLedger rows to IAM ids. Do not store Keycloak-specific columns.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS iam_user_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_iam_user_id ON users (iam_user_id)
    WHERE iam_user_id IS NOT NULL;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS iam_organization_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_organizations_iam_organization_id ON organizations (iam_organization_id)
    WHERE iam_organization_id IS NOT NULL;

ALTER TABLE platform.users
    ADD COLUMN IF NOT EXISTS iam_user_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_users_iam_user_id ON platform.users (iam_user_id)
    WHERE iam_user_id IS NOT NULL;
