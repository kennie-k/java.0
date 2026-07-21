
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE TABLE IF NOT EXISTS reviews (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_id  UUID        NOT NULL,
    seller_id    UUID        NOT NULL,
    property_id  UUID        NOT NULL,
    payment_id   UUID        UNIQUE NOT NULL,
    rating       INTEGER     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    is_verified  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_review_seller   ON reviews(seller_id);
CREATE INDEX IF NOT EXISTS idx_review_property ON reviews(property_id);
CREATE INDEX IF NOT EXISTS idx_review_reviewer ON reviews(reviewer_id);
