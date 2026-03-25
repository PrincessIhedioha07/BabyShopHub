-- BabyShopHub — Flyway V3: OTP / Verification Token table
-- Used for email verification and password-reset OTP flows.

CREATE TABLE IF NOT EXISTS verification_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(10)  NOT NULL,
    purpose     VARCHAR(30)  NOT NULL, -- EMAIL_VERIFY | PASSWORD_RESET
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vt_user_purpose ON verification_tokens(user_id, purpose, used);
