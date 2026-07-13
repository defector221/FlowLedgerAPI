-- Serial numbers
ALTER TABLE serial_numbers
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;