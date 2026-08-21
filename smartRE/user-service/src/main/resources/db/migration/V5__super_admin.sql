ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one row can ever have is_super_admin = true.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_one_super_admin
    ON users (is_super_admin) WHERE is_super_admin = true;

UPDATE users SET role = 'ADMIN', is_super_admin = true WHERE email = 'smartre@gmail.com';
