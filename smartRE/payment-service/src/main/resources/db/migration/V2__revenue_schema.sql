CREATE TABLE IF NOT EXISTS company_revenue (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id                      UUID         NOT NULL UNIQUE,
    buyer_id                        UUID         NOT NULL,
    seller_id                       UUID         NOT NULL,
    property_id                     UUID         NOT NULL,
    revenue_type                    VARCHAR(40)  NOT NULL,
    gross_amount                    NUMERIC(15,2) NOT NULL,
    platform_fee                    NUMERIC(15,2) NOT NULL,
    seller_payout                   NUMERIC(15,2) NOT NULL,
    fee_percentage                  NUMERIC(5,2)  NOT NULL,
    currency                        VARCHAR(5)   NOT NULL DEFAULT 'KES',
    status                          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    seller_payout_receipt           VARCHAR(50),
    seller_payout_at                TIMESTAMPTZ,
    b2c_conversation_id             VARCHAR(100),
    b2c_originator_conversation_id  VARCHAR(100),
    payout_failure_reason           TEXT,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rev_payment  ON company_revenue(payment_id);
CREATE INDEX IF NOT EXISTS idx_rev_seller   ON company_revenue(seller_id);
CREATE INDEX IF NOT EXISTS idx_rev_buyer    ON company_revenue(buyer_id);
CREATE INDEX IF NOT EXISTS idx_rev_type     ON company_revenue(revenue_type);
CREATE INDEX IF NOT EXISTS idx_rev_status   ON company_revenue(status);
CREATE INDEX IF NOT EXISTS idx_rev_b2c_orig ON company_revenue(b2c_originator_conversation_id);
