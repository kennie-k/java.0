CREATE TABLE IF NOT EXISTS review_audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID        NOT NULL,
    event_type  VARCHAR(60) NOT NULL,
    actor_id    UUID,
    actor_ip    VARCHAR(50),
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ral_review ON review_audit_log(review_id);
CREATE INDEX IF NOT EXISTS idx_ral_time   ON review_audit_log(created_at);
