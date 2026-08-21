-- Tracks the uploader of every document stored via /api/documents/upload so that
-- DocumentUploadController.serveFile can enforce "only the uploader or an admin may read this
-- document" instead of allowing any authenticated user to fetch any document by URL.
CREATE TABLE IF NOT EXISTS uploaded_documents (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key   VARCHAR(512) UNIQUE NOT NULL,
    category     VARCHAR(100) NOT NULL,
    uploader_id  UUID        REFERENCES users(id),
    content_type VARCHAR(100),
    size_bytes   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_uploaded_documents_uploader ON uploaded_documents(uploader_id);
