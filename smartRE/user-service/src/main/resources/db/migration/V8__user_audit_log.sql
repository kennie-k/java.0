-- Audit trail for sensitive admin actions against user accounts (role promotion, ban, unban).
-- Mirrors verification-service's verification_audit_log table.
CREATE TABLE IF NOT EXISTS user_audit_log (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id     UUID        NOT NULL REFERENCES users(id),
    action             VARCHAR(60) NOT NULL,
    performed_by       UUID        REFERENCES users(id),
    performed_by_role  VARCHAR(30),
    from_value         VARCHAR(60),
    to_value           VARCHAR(60),
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_audit_log_target ON user_audit_log(target_user_id);
CREATE INDEX IF NOT EXISTS idx_user_audit_log_performed_by ON user_audit_log(performed_by);
