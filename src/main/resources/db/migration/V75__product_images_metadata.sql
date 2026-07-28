-- Product image metadata for demo seeding / richer media
ALTER TABLE product_images
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS width INT,
    ADD COLUMN IF NOT EXISTS height INT,
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_role VARCHAR(32) NOT NULL DEFAULT 'MAIN';

CREATE INDEX IF NOT EXISTS idx_product_images_org_checksum
    ON product_images (organization_id, checksum)
    WHERE checksum IS NOT NULL;
