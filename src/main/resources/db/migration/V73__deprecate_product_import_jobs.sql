-- Optional: mark legacy product_import_jobs as superseded by import_jobs

COMMENT ON TABLE product_import_jobs IS
    'Deprecated: use import_jobs from V71 migration platform. Kept for historical rows.';
