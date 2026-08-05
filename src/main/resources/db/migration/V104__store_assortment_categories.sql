-- Store assortment: optional category allow-list on commerce profiles

ALTER TABLE store_commerce_profiles
    ADD COLUMN IF NOT EXISTS allowed_category_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Prefer fashion categories for Fashion Hub stores when empty allow-list
UPDATE store_commerce_profiles scp
SET allowed_category_ids = (
    SELECT COALESCE(jsonb_agg(c.id), '[]'::jsonb)
    FROM categories c
    WHERE c.organization_id = scp.organization_id
      AND lower(c.name) LIKE 'fashion%'
)
FROM retail_stores rs
WHERE rs.id = scp.store_id
  AND lower(rs.name) LIKE '%fashion%'
  AND (scp.allowed_category_ids IS NULL OR scp.allowed_category_ids = '[]'::jsonb);
