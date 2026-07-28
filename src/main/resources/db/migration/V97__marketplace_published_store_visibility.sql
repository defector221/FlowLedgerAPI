-- Published marketplace stores should be discoverable (visibility was defaulting to PRIVATE).
UPDATE store_commerce_profiles
SET visibility = 'PUBLIC'
WHERE published_to_marketplace = TRUE
  AND visibility = 'PRIVATE';

UPDATE marketplace_store_index
SET visibility = 'PUBLIC'
WHERE published = TRUE
  AND (visibility IS NULL OR visibility = 'PRIVATE');
