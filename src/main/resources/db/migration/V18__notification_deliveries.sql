CREATE TABLE notification_deliveries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID REFERENCES organizations(id) ON DELETE CASCADE,
    notification_type   VARCHAR(50) NOT NULL,
    channel             VARCHAR(30) NOT NULL,
    recipient           VARCHAR(255),
    subject             VARCHAR(255),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_ref        VARCHAR(255),
    error_message       TEXT,
    related_entity_type VARCHAR(100),
    related_entity_id   UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_delivery_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'IN_APP', 'SMS')),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_notification_deliveries_org ON notification_deliveries(organization_id, created_at DESC);
CREATE INDEX idx_notification_deliveries_related ON notification_deliveries(related_entity_type, related_entity_id);

CREATE INDEX IF NOT EXISTS idx_in_app_notifications_unread
    ON in_app_notifications(user_id, created_at DESC)
    WHERE read_at IS NULL;
