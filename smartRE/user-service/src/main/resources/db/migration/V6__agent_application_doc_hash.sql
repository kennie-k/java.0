ALTER TABLE agent_applications
    ADD COLUMN IF NOT EXISTS business_doc_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_agent_applications_doc_hash ON agent_applications(business_doc_hash);
