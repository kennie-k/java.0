ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS duplicate_parcel_flag BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_properties_status ON properties(status);
