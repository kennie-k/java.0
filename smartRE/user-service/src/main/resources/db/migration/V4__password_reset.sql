ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reset_token_hash   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reset_token_expiry TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_reset_token_hash ON users(reset_token_hash);
