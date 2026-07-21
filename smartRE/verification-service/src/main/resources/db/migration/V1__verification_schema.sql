CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS seller_identity_verifications (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        UNIQUE NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    identity_score      INTEGER     NOT NULL DEFAULT 0,
    badge_level         VARCHAR(20) NOT NULL DEFAULT 'NONE',
    rejection_reason    TEXT,
    resubmission_notes  TEXT,
    kyc_reference       VARCHAR(100),
    reviewed_by         UUID,
    reviewed_at         TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS seller_identity_documents (
    id                              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_identity_verification_id UUID        NOT NULL
        REFERENCES seller_identity_verifications(id) ON DELETE CASCADE,
    document_category               VARCHAR(60) NOT NULL,
    document_url                    TEXT        NOT NULL,
    file_hash_sha256                VARCHAR(64),
    file_size_bytes                 BIGINT,
    mime_type                       VARCHAR(50),
    is_required                     BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_authenticity_score           INTEGER,
    ai_tamper_detected              BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_metadata_clean               BOOLEAN,
    ai_font_consistency             BOOLEAN,
    ai_signature_detected           BOOLEAN,
    ai_seal_detected                BOOLEAN,
    ai_screening_notes              TEXT,
    ai_screened_at                  TIMESTAMPTZ,
    human_verified                  BOOLEAN,
    human_reviewer_id               UUID,
    human_review_notes              TEXT,
    human_reviewed_at               TIMESTAMPTZ,
    uploaded_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS property_ownership_verifications (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id                 UUID        NOT NULL,
    seller_identity_verif_id    UUID        NOT NULL
        REFERENCES seller_identity_verifications(id),
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    property_type               VARCHAR(30) NOT NULL,
    county                      VARCHAR(100),
    parcel_number               VARCHAR(100),
    title_deed_number           VARCHAR(100),
    lr_number                   VARCHAR(100),
    ownership_score             INTEGER     NOT NULL DEFAULT 0,
    ministry_lands_confirmed    BOOLEAN     NOT NULL DEFAULT FALSE,
    encumbrance_clear           BOOLEAN     NOT NULL DEFAULT FALSE,
    rejection_reason            TEXT,
    reviewed_by                 UUID,
    reviewed_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS property_ownership_documents (
    id                              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_ownership_verif_id     UUID        NOT NULL
        REFERENCES property_ownership_verifications(id) ON DELETE CASCADE,
    document_category               VARCHAR(60) NOT NULL,
    document_url                    TEXT        NOT NULL,
    file_hash_sha256                VARCHAR(64),
    file_size_bytes                 BIGINT,
    mime_type                       VARCHAR(50),
    is_required                     BOOLEAN     NOT NULL DEFAULT TRUE,
    lc_advocate_stamp_present       BOOLEAN,
    lc_advocate_signature_present   BOOLEAN,
    lc_commissioner_oaths_present   BOOLEAN,
    lc_official_seal_present        BOOLEAN,
    lc_owner_signature_present      BOOLEAN,
    lc_witness_signatures_present   BOOLEAN,
    lc_date_present                 BOOLEAN,
    lc_parcel_number_matches        BOOLEAN,
    lc_original_document_confirmed  BOOLEAN,
    ai_authenticity_score           INTEGER,
    ai_tamper_detected              BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_alteration_detected          BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_font_consistency             BOOLEAN,
    ai_date_sequence_valid          BOOLEAN,
    ai_metadata_clean               BOOLEAN,
    ai_screening_notes              TEXT,
    ai_screened_at                  TIMESTAMPTZ,
    human_legal_approved            BOOLEAN,
    human_reviewer_id               UUID,
    human_review_notes              TEXT,
    human_reviewed_at               TIMESTAMPTZ,
    uploaded_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ownership_document_requirements (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    property_type     VARCHAR(30) NOT NULL,
    document_category VARCHAR(60) NOT NULL,
    is_mandatory      BOOLEAN     NOT NULL DEFAULT TRUE,
    description       TEXT,
    kenya_law_ref     TEXT,
    UNIQUE(property_type, document_category)
);

CREATE TABLE IF NOT EXISTS verification_audit_log (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id     UUID        NOT NULL,
    verification_track  VARCHAR(30) NOT NULL,
    action              VARCHAR(60) NOT NULL,
    performed_by        UUID,
    performed_by_role   VARCHAR(20),
    from_status         VARCHAR(30),
    to_status           VARCHAR(30),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_siv_user      ON seller_identity_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_siv_status    ON seller_identity_verifications(status);
CREATE INDEX IF NOT EXISTS idx_sid_verif     ON seller_identity_documents(seller_identity_verification_id);
CREATE INDEX IF NOT EXISTS idx_pov_property  ON property_ownership_verifications(property_id);
CREATE INDEX IF NOT EXISTS idx_pov_status    ON property_ownership_verifications(status);
CREATE INDEX IF NOT EXISTS idx_pod_verif     ON property_ownership_documents(property_ownership_verif_id);
CREATE INDEX IF NOT EXISTS idx_audit_verif   ON verification_audit_log(verification_id);

INSERT INTO ownership_document_requirements
    (property_type, document_category, is_mandatory, description, kenya_law_ref)
VALUES
('FREEHOLD','TITLE_DEED',              true, 'Original Certificate of Title',                      'Land Registration Act 2012 s.26'),
('FREEHOLD','LAND_SEARCH_CERTIFICATE', true, 'Official Ardhisasa search, max 30 days old',         'Land Registration Act 2012 s.9'),
('FREEHOLD','LAND_RENT_CLEARANCE',     true, 'Land Rent Clearance Certificate',                    'Land Act 2012 s.74'),
('FREEHOLD','RATES_CLEARANCE_CERTIFICATE',true,'County council rates clearance',                   'Rating Act Cap 267'),
('FREEHOLD','CONSENT_TO_TRANSFER',     true, 'Land Control Board consent',                         'Land Control Act Cap 302'),
('FREEHOLD','TRANSFER_FORM_RL1',       true, 'Executed RL1 Transfer Form',                         'Land Registration Act 2012 s.57'),
('FREEHOLD','SURVEY_MAP',              true, 'Registry Index Map with parcel boundaries',          'Survey Act Cap 299'),
('FREEHOLD','SPOUSAL_CONSENT',         false,'Spousal consent if matrimonial home',                'Matrimonial Property Act 2013 s.12'),
('LEASEHOLD','TITLE_DEED',             true, 'Certificate of Lease',                               'Land Registration Act 2012 s.26'),
('LEASEHOLD','LAND_SEARCH_CERTIFICATE',true, 'Official Ardhisasa search',                          'Land Registration Act 2012 s.9'),
('LEASEHOLD','LAND_RENT_CLEARANCE',    true, 'Land rent clearance',                                'Land Act 2012 s.74'),
('LEASEHOLD','RATES_CLEARANCE_CERTIFICATE',true,'County rates clearance',                          'Rating Act Cap 267'),
('LEASEHOLD','CONSENT_TO_TRANSFER',    true, 'Commissioner of Lands consent',                      'Land Act 2012'),
('LEASEHOLD','TRANSFER_FORM_RL1',      true, 'RL1 Transfer Form',                                  'Land Registration Act 2012 s.57'),
('SECTIONAL_TITLE','TITLE_DEED',       true, 'Sectional Title Certificate',                        'Sectional Properties Act 2020'),
('SECTIONAL_TITLE','LAND_SEARCH_CERTIFICATE',true,'Official search',                               'Land Registration Act 2012 s.9'),
('SECTIONAL_TITLE','RATES_CLEARANCE_CERTIFICATE',true,'Management company and county clearance',   'Sectional Properties Act 2020 s.18'),
('SECTIONAL_TITLE','SERVICE_CHARGE_CLEARANCE',true,'Service charge clearance',                     'Sectional Properties Act 2020'),
('AGRICULTURAL','TITLE_DEED',          true, 'Original Title Deed',                                'Land Registration Act 2012 s.26'),
('AGRICULTURAL','LAND_SEARCH_CERTIFICATE',true,'Official Ardhisasa search',                        'Land Registration Act 2012 s.9'),
('AGRICULTURAL','LAND_CONTROL_BOARD_CONSENT',true,'LCB consent mandatory for agricultural land',  'Land Control Act Cap 302 s.6'),
('AGRICULTURAL','LAND_RENT_CLEARANCE', true, 'Land rent clearance',                                'Land Act 2012 s.74'),
('AGRICULTURAL','SURVEY_MAP',          true, 'Survey map showing acreage and boundaries',          'Survey Act Cap 299'),
('COMMERCIAL','TITLE_DEED',            true, 'Certificate of Title or Lease',                      'Land Registration Act 2012 s.26'),
('COMMERCIAL','LAND_SEARCH_CERTIFICATE',true,'Official search',                                    'Land Registration Act 2012 s.9'),
('COMMERCIAL','RATES_CLEARANCE_CERTIFICATE',true,'County council rates clearance',                 'Rating Act Cap 267')
ON CONFLICT (property_type, document_category) DO NOTHING;
