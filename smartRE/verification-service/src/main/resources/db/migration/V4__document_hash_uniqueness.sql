-- Application-level duplicate checks (SELECT exists, then INSERT) are subject to a
-- TOCTOU race: two concurrent uploads of the same file (e.g. bulk-uploading the same
-- image as both NATIONAL_ID_FRONT and NATIONAL_ID_BACK) can both pass the "does this
-- hash already exist" check before either commits. A DB-level unique constraint closes
-- that race — the second concurrent insert fails at the database and is turned into a
-- DuplicateDocumentException + fraud flag by the service layer.
-- Postgres treats each NULL as distinct under a UNIQUE constraint, so rows without a
-- computed hash (analysis fetch failure) are unaffected.
ALTER TABLE seller_identity_documents
    ADD CONSTRAINT uk_sid_file_hash UNIQUE (file_hash_sha256);

ALTER TABLE property_ownership_documents
    ADD CONSTRAINT uk_pod_file_hash UNIQUE (file_hash_sha256);
