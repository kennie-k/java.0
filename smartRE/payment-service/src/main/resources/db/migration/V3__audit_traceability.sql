ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS initiated_by_ip    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS escrow_released_by  UUID;

CREATE TABLE IF NOT EXISTS payment_audit_log (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id       UUID        NOT NULL,
    revenue_id       UUID,
    event_type       VARCHAR(60) NOT NULL,
    previous_status  VARCHAR(30),
    new_status       VARCHAR(30),
    actor_id         UUID,
    actor_role       VARCHAR(30),
    actor_ip         VARCHAR(50),
    mpesa_receipt    VARCHAR(50),
    amount_kes       NUMERIC(15,2),
    detail           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mpesa_raw_callbacks (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    callback_type               VARCHAR(20) NOT NULL,
    checkout_request_id         VARCHAR(100),
    originator_conversation_id  VARCHAR(100),
    result_code                 INTEGER,
    result_desc                 VARCHAR(255),
    raw_payload                 TEXT        NOT NULL,
    payment_id                  UUID,
    revenue_id                  UUID,
    received_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_receipts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number      VARCHAR(50) UNIQUE NOT NULL,
    payment_id          UUID        NOT NULL UNIQUE,
    buyer_id            UUID        NOT NULL,
    seller_id           UUID        NOT NULL,
    property_id         UUID        NOT NULL,
    payment_type        VARCHAR(30) NOT NULL,
    gross_amount        NUMERIC(15,2) NOT NULL,
    platform_fee        NUMERIC(15,2) NOT NULL DEFAULT 0,
    seller_payout       NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(5)  NOT NULL DEFAULT 'KES',
    mpesa_receipt       VARCHAR(50),
    payer_phone         VARCHAR(20),
    payer_name          VARCHAR(255),
    payee_identifier    VARCHAR(100),
    payee_type          VARCHAR(20) NOT NULL DEFAULT 'MPESA',
    status              VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    voided_at           TIMESTAMPTZ,
    void_reason         TEXT
);

ALTER TABLE company_revenue
    ADD COLUMN IF NOT EXISTS released_by_admin_id UUID,
    ADD COLUMN IF NOT EXISTS release_notes         TEXT,
    ADD COLUMN IF NOT EXISTS payout_method         VARCHAR(20) NOT NULL DEFAULT 'MPESA_B2C',
    ADD COLUMN IF NOT EXISTS payout_identifier     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payout_account_name   VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_pal_payment   ON payment_audit_log(payment_id);
CREATE INDEX IF NOT EXISTS idx_pal_revenue   ON payment_audit_log(revenue_id);
CREATE INDEX IF NOT EXISTS idx_pal_event     ON payment_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_pal_time      ON payment_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_raw_checkout  ON mpesa_raw_callbacks(checkout_request_id);
CREATE INDEX IF NOT EXISTS idx_raw_orig      ON mpesa_raw_callbacks(originator_conversation_id);
CREATE INDEX IF NOT EXISTS idx_raw_payment   ON mpesa_raw_callbacks(payment_id);
CREATE INDEX IF NOT EXISTS idx_receipt_pay   ON payment_receipts(payment_id);
CREATE INDEX IF NOT EXISTS idx_receipt_buyer ON payment_receipts(buyer_id);
CREATE INDEX IF NOT EXISTS idx_receipt_num   ON payment_receipts(receipt_number);
