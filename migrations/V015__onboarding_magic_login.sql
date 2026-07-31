-- One-time magic-login token for the "Continue Your Application" email link,
-- so applicants can skip retyping the emailed temp credentials.
ALTER TABLE users ADD COLUMN onboarding_login_token        VARCHAR(64);
ALTER TABLE users ADD COLUMN onboarding_login_token_expiry TIMESTAMP;

CREATE UNIQUE INDEX idx_users_onboarding_login_token ON users (onboarding_login_token);
