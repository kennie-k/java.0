CREATE TABLE IF NOT EXISTS agent_applications (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        UNIQUE NOT NULL REFERENCES users(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    business_name       VARCHAR(255),
    business_doc_url    TEXT        NOT NULL,
    rejection_reason    TEXT,
    reviewed_by         UUID,
    reviewed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_applications_status ON agent_applications(status);
