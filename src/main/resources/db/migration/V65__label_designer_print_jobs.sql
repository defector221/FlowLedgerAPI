ALTER TABLE retail_label_templates
    ADD COLUMN IF NOT EXISTS canvas_json JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS paper_size VARCHAR(40) DEFAULT '50x25mm',
    ADD COLUMN IF NOT EXISTS dpi INT NOT NULL DEFAULT 203;

CREATE TABLE IF NOT EXISTS label_template_fields (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id  UUID NOT NULL REFERENCES retail_label_templates(id) ON DELETE CASCADE,
    field_type   VARCHAR(40) NOT NULL,
    x            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    y            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    width        NUMERIC(10, 2) NOT NULL DEFAULT 10,
    height       NUMERIC(10, 2) NOT NULL DEFAULT 5,
    font_size    NUMERIC(6, 2),
    binding_key  VARCHAR(100),
    z_index      INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_label_template_fields_template
    ON label_template_fields (template_id, z_index);

CREATE TABLE IF NOT EXISTS barcode_print_jobs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    template_id      UUID REFERENCES retail_label_templates(id),
    filter_json      JSONB NOT NULL DEFAULT '{}',
    copies           INT NOT NULL DEFAULT 1,
    output_object_key VARCHAR(500),
    created_by       UUID,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_barcode_print_jobs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_barcode_print_jobs_org_status
    ON barcode_print_jobs (organization_id, status, created_at DESC);
