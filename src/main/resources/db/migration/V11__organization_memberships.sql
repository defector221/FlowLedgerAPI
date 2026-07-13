-- Multi-organization membership model

CREATE TABLE organization_memberships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invitation_token    VARCHAR(255),
    invitation_expiry   TIMESTAMPTZ,
    last_active_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_org_membership_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT chk_membership_status CHECK (status IN ('INVITED', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE organization_membership_roles (
    membership_id UUID NOT NULL REFERENCES organization_memberships(id) ON DELETE CASCADE,
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

CREATE INDEX idx_org_memberships_user ON organization_memberships(user_id);
CREATE INDEX idx_org_memberships_org ON organization_memberships(organization_id);
CREATE INDEX idx_org_memberships_invitation ON organization_memberships(invitation_token) WHERE invitation_token IS NOT NULL;

ALTER TABLE users ADD COLUMN last_active_organization_id UUID REFERENCES organizations(id);

-- Backfill memberships from existing users
INSERT INTO organization_memberships (user_id, organization_id, status, invitation_token, invitation_expiry, created_at, updated_at)
SELECT
    u.id,
    u.organization_id,
    CASE
        WHEN u.user_status = 'INVITED' THEN 'INVITED'
        WHEN u.user_status = 'INACTIVE' THEN 'INACTIVE'
        ELSE 'ACTIVE'
    END,
    u.invitation_token,
    u.invitation_expiry,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.organization_id IS NOT NULL;

-- Backfill membership roles from user_roles
INSERT INTO organization_membership_roles (membership_id, role_id)
SELECT om.id, ur.role_id
FROM user_roles ur
JOIN organization_memberships om ON om.user_id = ur.user_id;

-- Set last active organization from current assignment
UPDATE users SET last_active_organization_id = organization_id WHERE organization_id IS NOT NULL;
