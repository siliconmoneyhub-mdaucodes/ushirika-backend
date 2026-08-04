-- Onboarding/profile refactor Phase 1: structured address (country + Kenya/Uganda
-- sub-regions) replacing the free-text address/county pair, and relaxing identity
-- columns to nullable since a bare MemberProfile row is now valid before onboarding
-- fills it in (completeness is enforced in OnboardingService, not the DB).
ALTER TABLE member_profiles ADD COLUMN street VARCHAR(300);
ALTER TABLE member_profiles ADD COLUMN city VARCHAR(150);
ALTER TABLE member_profiles ADD COLUMN zip_code VARCHAR(20);
ALTER TABLE member_profiles ADD COLUMN country VARCHAR(10) CHECK (country IN ('KENYA','UGANDA'));
ALTER TABLE member_profiles ADD COLUMN kenya_county VARCHAR(100);
ALTER TABLE member_profiles ADD COLUMN kenya_sub_county VARCHAR(100);
ALTER TABLE member_profiles ADD COLUMN kenya_village VARCHAR(100);
ALTER TABLE member_profiles ADD COLUMN uganda_province VARCHAR(100);
ALTER TABLE member_profiles ADD COLUMN uganda_county VARCHAR(100);
ALTER TABLE member_profiles ADD COLUMN uganda_village VARCHAR(100);

ALTER TABLE member_profiles DROP COLUMN address;
ALTER TABLE member_profiles DROP COLUMN county;

ALTER TABLE member_profiles ALTER COLUMN id_number DROP NOT NULL;
ALTER TABLE member_profiles ALTER COLUMN date_of_birth DROP NOT NULL;
ALTER TABLE member_profiles ALTER COLUMN gender DROP NOT NULL;

DROP INDEX IF EXISTS idx_mp_county;
CREATE INDEX idx_mp_country ON member_profiles (country);
