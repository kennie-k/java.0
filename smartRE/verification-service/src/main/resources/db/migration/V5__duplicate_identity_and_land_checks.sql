-- National ID number extracted from Smile Identity biometric verification was
-- being discarded — persist it and enforce cross-account uniqueness so the
-- same real-world National ID cannot be verified against two accounts.
ALTER TABLE seller_identity_verifications ADD COLUMN national_id_number VARCHAR(50);

CREATE UNIQUE INDEX uk_siv_national_id ON seller_identity_verifications (national_id_number)
    WHERE national_id_number IS NOT NULL;

-- Land identifiers (parcel/title deed/LR number) had no uniqueness check at
-- all, so the same real-world parcel could be claimed under two different
-- property listings. Rejected verifications are excluded so a legitimately
-- corrected resubmission under a new property draft isn't blocked forever.
CREATE UNIQUE INDEX uk_pov_parcel_number ON property_ownership_verifications (parcel_number)
    WHERE parcel_number IS NOT NULL AND status <> 'REJECTED';

CREATE UNIQUE INDEX uk_pov_title_deed_number ON property_ownership_verifications (title_deed_number)
    WHERE title_deed_number IS NOT NULL AND status <> 'REJECTED';

CREATE UNIQUE INDEX uk_pov_lr_number ON property_ownership_verifications (lr_number)
    WHERE lr_number IS NOT NULL AND status <> 'REJECTED';
