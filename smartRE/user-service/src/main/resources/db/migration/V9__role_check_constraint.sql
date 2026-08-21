-- Belt-and-braces: the application enforces valid roles via the Role enum, but the column
-- itself accepted any VARCHAR(20). Add a CHECK constraint so bad data can't enter the table
-- through any path that bypasses the application layer (manual SQL, another service, etc).
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('BUYER', 'SELLER', 'AGENT', 'ADMIN'));
