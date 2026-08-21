-- Transactional outbox for payment-events Kafka publishing.
-- A row is inserted in the SAME transaction as the payment status update that
-- triggers the event (see PaymentService.handleCallback / reconcileOne). A
-- background sweeper (PaymentOutboxSweeper) retries anything not yet
-- acknowledged as published, so a Kafka broker outage or transient send
-- failure can never silently lose a PAYMENT_COMPLETED event (and therefore
-- never silently fail to unlock a review).
CREATE TABLE IF NOT EXISTS payment_outbox_events (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   UUID        NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT        NOT NULL,
    published    BOOLEAN     NOT NULL DEFAULT FALSE,
    attempts     INTEGER     NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON payment_outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_payment      ON payment_outbox_events(payment_id);
