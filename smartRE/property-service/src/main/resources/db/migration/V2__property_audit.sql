CREATE TABLE IF NOT EXISTS property_audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id     UUID        NOT NULL,
    event_type      VARCHAR(60) NOT NULL,
    previous_status VARCHAR(30),
    new_status      VARCHAR(30),
    actor_id        UUID,
    actor_role      VARCHAR(30),
    actor_ip        VARCHAR(50),
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_propal_prop  ON property_audit_log(property_id);
CREATE INDEX IF NOT EXISTS idx_propal_event ON property_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_propal_time  ON property_audit_log(created_at);
