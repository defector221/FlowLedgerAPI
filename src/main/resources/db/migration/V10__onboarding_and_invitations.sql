ALTER TABLE organizations
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN onboarding_completed_at TIMESTAMPTZ;

ALTER TABLE users
    ADD COLUMN user_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN invitation_token VARCHAR(255),
    ADD COLUMN invitation_expiry TIMESTAMPTZ;

UPDATE organizations SET onboarding_completed = TRUE WHERE created_at < NOW();

UPDATE users SET user_status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;

CREATE INDEX idx_users_invitation_token ON users(invitation_token) WHERE invitation_token IS NOT NULL;
