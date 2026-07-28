ALTER TABLE commerce_collect_tokens
    ADD COLUMN IF NOT EXISTS collect_code VARCHAR(6);

CREATE UNIQUE INDEX IF NOT EXISTS idx_collect_tokens_active_code
    ON commerce_collect_tokens(collect_code)
    WHERE verified_at IS NULL AND collect_code IS NOT NULL;
