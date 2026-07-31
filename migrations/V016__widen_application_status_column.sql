-- membership_applications.status was VARCHAR(20), but ApplicationStatus.ONBOARDING_IN_PROGRESS
-- is 22 characters — Hibernate could never actually persist that transition.
ALTER TABLE membership_applications ALTER COLUMN status TYPE VARCHAR(30);
