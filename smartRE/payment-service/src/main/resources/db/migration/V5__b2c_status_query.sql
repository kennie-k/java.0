-- Tracks outgoing M-Pesa B2C TransactionStatusQuery attempts so
-- B2cReconciliationJob can actively re-query Safaricom for payouts stuck in
-- PAYOUT_INITIATED (mirroring the STK status-query reconciliation already
-- done for buyer payments), instead of only logging a manual-action alert.
ALTER TABLE company_revenue
    ADD COLUMN IF NOT EXISTS status_query_conversation_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status_query_sent_at         TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_rev_status_query_conv ON company_revenue(status_query_conversation_id);
