-- Onboarding/profile refactor Phase 2: next-of-kin and emergency-contact become
-- real child tables, fixed at exactly two entries each (position 1/2), replacing
-- the old single flat set of columns on member_profiles.
CREATE TABLE member_next_of_kin (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_profile_id  UUID NOT NULL REFERENCES member_profiles(id),
    position           SMALLINT NOT NULL CHECK (position IN (1, 2)),
    full_name          VARCHAR(150) NOT NULL,
    phone              VARCHAR(20)  NOT NULL,
    relationship       VARCHAR(50)  NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(150),
    updated_by         VARCHAR(150),
    CONSTRAINT uq_nok_profile_position UNIQUE (member_profile_id, position)
);
CREATE INDEX idx_nok_profile ON member_next_of_kin (member_profile_id);

CREATE TABLE member_emergency_contacts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_profile_id  UUID NOT NULL REFERENCES member_profiles(id),
    position           SMALLINT NOT NULL CHECK (position IN (1, 2)),
    full_name          VARCHAR(150) NOT NULL,
    phone              VARCHAR(20)  NOT NULL,
    relationship       VARCHAR(50)  NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(150),
    updated_by         VARCHAR(150),
    CONSTRAINT uq_ec_profile_position UNIQUE (member_profile_id, position)
);
CREATE INDEX idx_ec_profile ON member_emergency_contacts (member_profile_id);

ALTER TABLE member_profiles DROP COLUMN next_of_kin_name;
ALTER TABLE member_profiles DROP COLUMN next_of_kin_phone;
ALTER TABLE member_profiles DROP COLUMN next_of_kin_relationship;
ALTER TABLE member_profiles DROP COLUMN emergency_contact_name;
ALTER TABLE member_profiles DROP COLUMN emergency_contact_phone;
