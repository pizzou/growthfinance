-- Email OTP: a lighter-weight second factor for login, used for users who
-- don't have full TOTP MFA enrolled (see AuthController.MFA_MANDATORY_ROLES
-- for which roles require TOTP instead). The code itself is never stored —
-- only a bcrypt hash of it, alongside its expiry and a small attempt counter
-- to stop brute-forcing a 6-digit code.

ALTER TABLE app_users ADD COLUMN login_otp_hash VARCHAR(255);
ALTER TABLE app_users ADD COLUMN login_otp_expires_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN login_otp_attempts INTEGER DEFAULT 0;
