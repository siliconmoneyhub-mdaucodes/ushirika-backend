-- Lets the onboarding magic-login link survive a few false starts (closed tab, dropped
-- connection) before it's fully spent, instead of invalidating on the very first use.
ALTER TABLE users ADD COLUMN onboarding_login_token_uses INTEGER NOT NULL DEFAULT 0
