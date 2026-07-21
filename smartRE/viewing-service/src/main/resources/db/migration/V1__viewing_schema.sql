
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE TABLE IF NOT EXISTS viewings (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id      UUID        NOT NULL,
    buyer_id         UUID        NOT NULL,
    seller_id        UUID        NOT NULL,
    scheduled_at     TIMESTAMPTZ NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    notes            TEXT,
    buyer_confirmed  BOOLEAN     NOT NULL DEFAULT FALSE,
    seller_confirmed BOOLEAN     NOT NULL DEFAULT FALSE,
    cancelled_by     UUID,
    cancellation_reason TEXT,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_view_property ON viewings(property_id);
CREATE INDEX IF NOT EXISTS idx_view_buyer    ON viewings(buyer_id);
CREATE INDEX IF NOT EXISTS idx_view_seller   ON viewings(seller_id);
CREATE INDEX IF NOT EXISTS idx_view_status   ON viewings(status);
