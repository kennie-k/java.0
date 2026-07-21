ALTER TABLE seller_identity_verifications
    ADD COLUMN IF NOT EXISTS fraud_strike_count  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS permanently_banned  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ban_reason          TEXT;

ALTER TABLE property_ownership_verifications
    ADD COLUMN IF NOT EXISTS fraud_strike_count  INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS verification_fraud_flags (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL,
    verification_id     UUID        NOT NULL,
    verification_track  VARCHAR(30) NOT NULL,
    fraud_type          VARCHAR(60) NOT NULL,
    document_category   VARCHAR(60),
    document_hash       VARCHAR(64),
    details             TEXT,
    flagged_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fraud_user    ON verification_fraud_flags(user_id);
CREATE INDEX IF NOT EXISTS idx_fraud_hash    ON verification_fraud_flags(document_hash);
CREATE INDEX IF NOT EXISTS idx_fraud_verif   ON verification_fraud_flags(verification_id);
