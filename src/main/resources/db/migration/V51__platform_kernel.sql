-- Wave 0: Platform kernel — approvals, attachments, comments, tags, document history
-- Polymorphic document refs (entity_type + entity_id) for ERP-wide reuse

CREATE TABLE IF NOT EXISTS approval_definitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(100) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    levels_json         TEXT NOT NULL DEFAULT '[{"level":1,"role":"ORGANIZATION_ADMIN"}]',
    min_amount          NUMERIC(19, 4),
    max_amount          NUMERIC(19, 4),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_approval_definitions_org_type_name UNIQUE (organization_id, entity_type, name)
);

CREATE INDEX idx_approval_definitions_org_type ON approval_definitions (organization_id, entity_type);

CREATE TABLE IF NOT EXISTS approval_instances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    definition_id       UUID REFERENCES approval_definitions(id) ON DELETE SET NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    current_level       INT NOT NULL DEFAULT 1,
    total_levels        INT NOT NULL DEFAULT 1,
    requested_by        UUID NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    amount              NUMERIC(19, 4),
    remarks             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_approval_instances_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_approval_instances_org_entity ON approval_instances (organization_id, entity_type, entity_id);
CREATE INDEX idx_approval_instances_org_status ON approval_instances (organization_id, status);

CREATE TABLE IF NOT EXISTS approval_instance_actions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    instance_id         UUID NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    level_number        INT NOT NULL,
    action              VARCHAR(30) NOT NULL,
    actor_id            UUID NOT NULL,
    acted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remarks             TEXT,
    CONSTRAINT chk_approval_actions_action CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL', 'RETURN'))
);

CREATE INDEX idx_approval_actions_instance ON approval_instance_actions (instance_id);

CREATE TABLE IF NOT EXISTS document_attachments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID NOT NULL,
    file_name           VARCHAR(500) NOT NULL,
    content_type        VARCHAR(200),
    size_bytes          BIGINT,
    storage_key         VARCHAR(1000) NOT NULL,
    uploaded_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_document_attachments_org_entity ON document_attachments (organization_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS document_comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID NOT NULL,
    parent_id           UUID REFERENCES document_comments(id) ON DELETE CASCADE,
    body                TEXT NOT NULL,
    author_id           UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_document_comments_org_entity ON document_comments (organization_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS document_tags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID NOT NULL,
    tag                 VARCHAR(100) NOT NULL,
    system_tag          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    CONSTRAINT uq_document_tags UNIQUE (organization_id, entity_type, entity_id, tag)
);

CREATE INDEX idx_document_tags_org_tag ON document_tags (organization_id, tag);

CREATE TABLE IF NOT EXISTS document_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    detail_json         TEXT,
    actor_id            UUID,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id      UUID
);

CREATE INDEX idx_document_history_org_entity ON document_history (organization_id, entity_type, entity_id, occurred_at DESC);

-- Branch-aware document sequences (nullable branch for org-wide numbering)
ALTER TABLE document_sequences
    ADD COLUMN IF NOT EXISTS branch_id UUID;

ALTER TABLE document_sequences DROP CONSTRAINT IF EXISTS uq_doc_seq;

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_sequences_org_type_fy_branch
    ON document_sequences (organization_id, document_type, financial_year, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'));

INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), v.code, v.name, v.module
FROM (VALUES
    ('APPROVAL_READ', 'View approvals', 'PLATFORM'),
    ('APPROVAL_WRITE', 'Manage approvals', 'PLATFORM'),
    ('ATTACHMENT_WRITE', 'Manage document attachments', 'PLATFORM'),
    ('COMMENT_WRITE', 'Manage document comments', 'PLATFORM')
) AS v(code, name, module)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code IN ('APPROVAL_READ', 'APPROVAL_WRITE', 'ATTACHMENT_WRITE', 'COMMENT_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('APPROVAL_READ', 'APPROVAL_WRITE')
WHERE r.code = 'ACCOUNTANT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
