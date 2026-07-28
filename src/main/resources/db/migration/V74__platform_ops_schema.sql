-- Platform Operations Center: isolated auth + audit in schema "platform"
CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE TABLE platform.roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE platform.permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE platform.role_permissions (
    role_id       UUID NOT NULL REFERENCES platform.roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES platform.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE platform.user_roles (
    user_id UUID NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES platform.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE platform.refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_refresh_tokens_user ON platform.refresh_tokens (user_id);

CREATE TABLE platform.audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID,
    actor_email     VARCHAR(255),
    actor_role      VARCHAR(64),
    action          VARCHAR(120) NOT NULL,
    organization_id UUID,
    target_type     VARCHAR(80),
    target_id       VARCHAR(120),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(500),
    status          VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    duration_ms     BIGINT,
    before_json     TEXT,
    after_json      TEXT,
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_audit_created ON platform.audit_logs (created_at DESC);
CREATE INDEX idx_platform_audit_org ON platform.audit_logs (organization_id);

-- Org lifecycle for ops (tenant table extension)
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_organizations_lifecycle ON organizations (lifecycle_status);
CREATE INDEX IF NOT EXISTS idx_organizations_created_at ON organizations (created_at);
CREATE INDEX IF NOT EXISTS idx_organizations_name_lower ON organizations (LOWER(name));

-- Permissions
INSERT INTO platform.permissions (id, code, name, description) VALUES
 (gen_random_uuid(), 'ORG_READ', 'Read organizations', NULL),
 (gen_random_uuid(), 'ORG_WRITE', 'Manage organizations', NULL),
 (gen_random_uuid(), 'ORG_IMPERSONATE', 'Impersonate tenant users', NULL),
 (gen_random_uuid(), 'SUBSCRIPTION_READ', 'Read subscriptions', NULL),
 (gen_random_uuid(), 'SUBSCRIPTION_WRITE', 'Manage subscriptions', NULL),
 (gen_random_uuid(), 'FEATURE_WRITE', 'Manage org features/modules', NULL),
 (gen_random_uuid(), 'DEMO_SEED', 'Seed demo data', NULL),
 (gen_random_uuid(), 'DEMO_READ', 'List demo scenarios', NULL),
 (gen_random_uuid(), 'AUDIT_READ', 'Read platform audit', NULL),
 (gen_random_uuid(), 'HEALTH_READ', 'Read platform health', NULL),
 (gen_random_uuid(), 'SETTINGS_WRITE', 'Platform settings', NULL),
 (gen_random_uuid(), 'DASHBOARD_READ', 'Read ops dashboard', NULL);

INSERT INTO platform.roles (id, code, name, description) VALUES
 (gen_random_uuid(), 'PLATFORM_ADMIN', 'Platform Admin', 'Full platform control'),
 (gen_random_uuid(), 'PLATFORM_SUPPORT', 'Platform Support', 'Support engineer access');

-- PLATFORM_ADMIN gets all permissions
INSERT INTO platform.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code = 'PLATFORM_ADMIN';

-- PLATFORM_SUPPORT: read + limited act
INSERT INTO platform.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM platform.roles r
JOIN platform.permissions p ON p.code IN (
    'ORG_READ', 'ORG_WRITE', 'ORG_IMPERSONATE', 'SUBSCRIPTION_READ',
    'DEMO_SEED', 'DEMO_READ', 'AUDIT_READ', 'HEALTH_READ', 'DASHBOARD_READ', 'FEATURE_WRITE'
)
WHERE r.code = 'PLATFORM_SUPPORT';
