-- Onboarding refactor Phase 3: separate constitution consent from bylaws consent
-- (each is now its own onboarding step), plus tracking timestamps for the new
-- identity/address/kin-contacts onboarding steps landing in Phase 4.
ALTER TABLE membership_applications ADD COLUMN constitution_accepted_at TIMESTAMP;
ALTER TABLE membership_applications ADD COLUMN identity_info_submitted_at TIMESTAMP;
ALTER TABLE membership_applications ADD COLUMN address_info_submitted_at TIMESTAMP;
ALTER TABLE membership_applications ADD COLUMN kin_contacts_submitted_at TIMESTAMP;
