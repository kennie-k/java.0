CREATE TABLE IF NOT EXISTS property_image_hashes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID        NOT NULL,
    seller_id   UUID        NOT NULL,
    image_url   TEXT        NOT NULL,
    image_hash  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_property_image_hashes_hash ON property_image_hashes(image_hash);
CREATE INDEX IF NOT EXISTS idx_property_image_hashes_property ON property_image_hashes(property_id);
