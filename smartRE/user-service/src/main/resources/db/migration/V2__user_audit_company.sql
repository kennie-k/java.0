ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_type       VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN IF NOT EXISTS company_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS company_reg_number  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS kra_pin             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS paybill_number      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS till_number         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bank_account_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_name           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_branch         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_swift_code     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS preferred_payout_method VARCHAR(20) NOT NULL DEFAULT 'MPESA',
    ADD COLUMN IF NOT EXISTS payout_phone        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_login_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_login_ip       VARCHAR(50);

CREATE TABLE IF NOT EXISTS user_audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    event_type      VARCHAR(60) NOT NULL,
    actor_id        UUID,
    actor_role      VARCHAR(30),
    actor_ip        VARCHAR(50),
    previous_value  TEXT,
    new_value       TEXT,
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ual_user   ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_ual_event  ON user_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_ual_time   ON user_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_usr_acct   ON users(account_type);
CREATE INDEX IF NOT EXISTS idx_usr_payout ON users(preferred_payout_method);
