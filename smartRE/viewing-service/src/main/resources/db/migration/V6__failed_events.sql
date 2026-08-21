-- Poison Kafka messages (exhausted all @RetryableTopic attempts) previously only ever hit a
-- log.error line with no durable, queryable record — meaning a stuck payment-completed event
-- could silently leave a viewing fee stuck at PENDING_FEE forever. This table gives operators
-- (and any future admin tooling) a place to find and resolve them.
CREATE TABLE IF NOT EXISTS failed_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_topic   VARCHAR(200) NOT NULL,
    event_type     VARCHAR(100),
    payload        TEXT,
    failure_reason TEXT,
    resolved       BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_failed_events_resolved ON failed_events(resolved);
