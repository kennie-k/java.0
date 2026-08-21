-- County is always an exact value from a fixed dropdown (see smartRE-front lib/counties.ts),
-- so its LIKE '%..%' filter was needless — switched to exact match in PropertyRepository,
-- which the existing idx_prop_county btree index already serves.
--
-- title/description/city are genuine free-text substring searches (public keyword search box,
-- free-typed "City / Area" field), so a leading-wildcard LIKE can never use a plain btree index.
-- pg_trgm + GIN lets Postgres serve those LIKE '%term%' queries via index instead of a seq scan.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_properties_title_trgm
    ON properties USING GIN (LOWER(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_properties_description_trgm
    ON properties USING GIN (LOWER(description) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_properties_city_trgm
    ON properties USING GIN (LOWER(city) gin_trgm_ops);
