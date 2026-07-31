-- Fraud/mismatch rejections are audited before any review row exists, so review_id
-- must be allowed to be null for those events.
ALTER TABLE review_audit_log ALTER COLUMN review_id DROP NOT NULL;
