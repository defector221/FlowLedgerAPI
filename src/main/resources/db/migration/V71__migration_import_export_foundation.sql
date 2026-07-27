-- Migration / Import-Export platform foundation

CREATE TABLE IF NOT EXISTS import_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    module              VARCHAR(50) NOT NULL,
    source_type         VARCHAR(30) NOT NULL DEFAULT 'CSV',
    status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    file_object_key     VARCHAR(500),
    file_name           VARCHAR(255),
    mapping_profile_id  UUID,
    total_rows          INT NOT NULL DEFAULT 0,
    processed_rows      INT NOT NULL DEFAULT 0,
    success_rows        INT NOT NULL DEFAULT 0,
    error_rows          INT NOT NULL DEFAULT 0,
    skipped_rows        INT NOT NULL DEFAULT 0,
    progress_pct        NUMERIC(5,2) NOT NULL DEFAULT 0,
    columns_json        JSONB NOT NULL DEFAULT '[]',
    mapping_json        JSONB NOT NULL DEFAULT '{}',
    error_summary       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_import_jobs_status CHECK (status IN (
        'PENDING', 'UPLOADED', 'DETECTED', 'MAPPED', 'VALIDATED',
        'QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    CONSTRAINT chk_import_jobs_source CHECK (source_type IN (
        'CSV', 'XLSX', 'JSON', 'XML', 'GENERIC'
    ))
);

CREATE INDEX IF NOT EXISTS idx_import_jobs_org_status
    ON import_jobs (organization_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_import_jobs_org_module
    ON import_jobs (organization_id, module, created_at DESC);

CREATE TABLE IF NOT EXISTS import_job_files (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_id              UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    sheet_name          VARCHAR(100),
    file_object_key     VARCHAR(500) NOT NULL,
    file_name           VARCHAR(255),
    role                VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_import_job_files_job ON import_job_files (job_id);

CREATE TABLE IF NOT EXISTS import_mapping_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    module              VARCHAR(50) NOT NULL,
    source_label        VARCHAR(100),
    auto_create_missing BOOLEAN NOT NULL DEFAULT FALSE,
    mappings_json       JSONB NOT NULL DEFAULT '[]',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_import_mapping_profiles_org_name UNIQUE (organization_id, name)
);

CREATE INDEX IF NOT EXISTS idx_import_mapping_profiles_module
    ON import_mapping_profiles (organization_id, module) WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS import_field_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    profile_id          UUID NOT NULL REFERENCES import_mapping_profiles(id) ON DELETE CASCADE,
    source_column       VARCHAR(200) NOT NULL,
    target_field        VARCHAR(200) NOT NULL,
    transform           VARCHAR(50),
    default_value       VARCHAR(500),
    required            BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_import_field_mappings_profile ON import_field_mappings (profile_id);

CREATE TABLE IF NOT EXISTS import_row_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_id              UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_number          INT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    raw_json            JSONB NOT NULL DEFAULT '{}',
    normalized_json     JSONB NOT NULL DEFAULT '{}',
    entity_type         VARCHAR(50),
    entity_id           UUID,
    message             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_import_row_results_job_row UNIQUE (job_id, row_number),
    CONSTRAINT chk_import_row_status CHECK (status IN (
        'PENDING', 'OK', 'WARNING', 'ERROR', 'IMPORTED', 'SKIPPED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_import_row_results_job_status
    ON import_row_results (job_id, status);

CREATE TABLE IF NOT EXISTS import_validation_errors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_id              UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_number          INT NOT NULL,
    field               VARCHAR(100),
    code                VARCHAR(50) NOT NULL,
    message             TEXT NOT NULL,
    severity            VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_import_validation_severity CHECK (severity IN ('ERROR', 'WARNING', 'INFO'))
);

CREATE INDEX IF NOT EXISTS idx_import_validation_errors_job
    ON import_validation_errors (job_id, row_number);

CREATE TABLE IF NOT EXISTS import_reports (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_id                  UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    summary_json            JSONB NOT NULL DEFAULT '{}',
    error_file_object_key   VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_import_reports_job UNIQUE (job_id)
);

CREATE TABLE IF NOT EXISTS export_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    module              VARCHAR(50) NOT NULL,
    format              VARCHAR(20) NOT NULL DEFAULT 'CSV',
    status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    file_object_key     VARCHAR(500),
    file_name           VARCHAR(255),
    total_rows          INT NOT NULL DEFAULT 0,
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_export_jobs_status CHECK (status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT chk_export_jobs_format CHECK (format IN ('CSV', 'XLSX', 'JSON'))
);

CREATE INDEX IF NOT EXISTS idx_export_jobs_org ON export_jobs (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS migration_audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_id              UUID,
    action              VARCHAR(50) NOT NULL,
    module              VARCHAR(50),
    detail_json         JSONB NOT NULL DEFAULT '{}',
    actor_id            UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_audit_org ON migration_audit_logs (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS import_synonyms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    synonym             VARCHAR(200) NOT NULL,
    target_field        VARCHAR(200) NOT NULL,
    module              VARCHAR(50)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_import_synonyms
    ON import_synonyms (lower(synonym), COALESCE(module, '*'));
CREATE INDEX IF NOT EXISTS idx_import_synonyms_target ON import_synonyms (target_field);
