ALTER TABLE viewings
    ALTER COLUMN status TYPE VARCHAR(30),
    ALTER COLUMN status SET DEFAULT 'PENDING_FEE',
    ADD COLUMN IF NOT EXISTS viewing_fee_payment_id UUID,
    ADD COLUMN IF NOT EXISTS viewing_fee_status     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS buyer_phone            VARCHAR(20) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_view_fee_payment ON viewings(viewing_fee_payment_id);
