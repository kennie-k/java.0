
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS payments (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_id               UUID        NOT NULL,
    seller_id              UUID        NOT NULL,
    property_id            UUID        NOT NULL,
    payment_type           VARCHAR(30) NOT NULL,
    amount                 NUMERIC(15,2) NOT NULL,
    currency               VARCHAR(5)  NOT NULL DEFAULT 'KES',
    status                 VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    phone_number           VARCHAR(20) NOT NULL,
    mpesa_checkout_request_id VARCHAR(100) UNIQUE,
    mpesa_merchant_request_id VARCHAR(100),
    mpesa_receipt_number   VARCHAR(50),
    mpesa_transaction_date VARCHAR(20),
    failure_reason         TEXT,
    escrow_released        BOOLEAN     NOT NULL DEFAULT FALSE,
    escrow_released_at     TIMESTAMPTZ,
    idempotency_key        VARCHAR(100) UNIQUE NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pay_buyer    ON payments(buyer_id);
CREATE INDEX IF NOT EXISTS idx_pay_seller   ON payments(seller_id);
CREATE INDEX IF NOT EXISTS idx_pay_property ON payments(property_id);
CREATE INDEX IF NOT EXISTS idx_pay_status   ON payments(status);
CREATE INDEX IF NOT EXISTS idx_pay_idem     ON payments(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_pay_checkout ON payments(mpesa_checkout_request_id);
