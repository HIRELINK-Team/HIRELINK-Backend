ALTER TABLE phone_verification ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE phone_verification
    ADD COLUMN signup_token_hash VARCHAR(64) UNIQUE REFERENCES social_auth_session(token_hash) ON DELETE CASCADE,
    ADD CONSTRAINT ck_phone_verification_owner CHECK (
        (user_id IS NOT NULL AND signup_token_hash IS NULL)
        OR (user_id IS NULL AND signup_token_hash IS NOT NULL)
    );

ALTER TABLE phone_verification_send_attempt ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE phone_verification_send_attempt
    ADD COLUMN signup_identity VARCHAR(64),
    ADD CONSTRAINT ck_phone_send_attempt_owner CHECK (
        (user_id IS NOT NULL AND signup_identity IS NULL)
        OR (user_id IS NULL AND signup_identity IS NOT NULL)
    );

CREATE INDEX idx_phone_send_attempt_signup_time
    ON phone_verification_send_attempt(signup_identity, requested_at);
