
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS properties (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id                   UUID         NOT NULL,
    title                       VARCHAR(500) NOT NULL,
    description                 TEXT,
    property_type               VARCHAR(30)  NOT NULL,
    listing_type                VARCHAR(20)  NOT NULL DEFAULT 'SALE',
    status                      VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    county                      VARCHAR(100) NOT NULL,
    sub_county                  VARCHAR(100),
    city                        VARCHAR(100),
    location_description        TEXT,
    latitude                    DOUBLE PRECISION,
    longitude                   DOUBLE PRECISION,
    price                       NUMERIC(15,2) NOT NULL,
    bedrooms                    INTEGER,
    bathrooms                   INTEGER,
    area_sqm                    DOUBLE PRECISION,
    year_built                  INTEGER,
    seller_identity_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    property_ownership_verified BOOLEAN NOT NULL DEFAULT FALSE,
    view_count                  INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS property_images (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    url         TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_prop_seller   ON properties(seller_id);
CREATE INDEX IF NOT EXISTS idx_prop_status   ON properties(status);
CREATE INDEX IF NOT EXISTS idx_prop_county   ON properties(county);
CREATE INDEX IF NOT EXISTS idx_prop_type     ON properties(property_type);
CREATE INDEX IF NOT EXISTS idx_prop_listing  ON properties(listing_type);
CREATE INDEX IF NOT EXISTS idx_prop_price    ON properties(price);
CREATE INDEX IF NOT EXISTS idx_prop_bedroom  ON properties(bedrooms);
CREATE INDEX IF NOT EXISTS idx_img_property  ON property_images(property_id);
