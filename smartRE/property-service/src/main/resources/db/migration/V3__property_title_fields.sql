
ALTER TABLE properties ADD COLUMN IF NOT EXISTS parcel_number VARCHAR(100);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS title_deed_number VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_properties_parcel_number ON properties(parcel_number);
CREATE INDEX IF NOT EXISTS idx_properties_title_deed_number ON properties(title_deed_number);
