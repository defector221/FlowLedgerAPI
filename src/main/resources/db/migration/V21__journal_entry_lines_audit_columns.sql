-- Align journal_entry_lines with AuditedEntity (created_by / updated_by)

ALTER TABLE journal_entry_lines
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;
