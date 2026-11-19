ALTER TABLE term_type ADD COLUMN is_student_registration BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO term_type (term_type_name, is_required, is_social_signup, is_student_registration)
VALUES ('고유식별정보 수집·이용', TRUE, TRUE, TRUE);

CREATE TABLE social_signup_registration (
    token_hash VARCHAR(64) PRIMARY KEY REFERENCES social_auth_session(token_hash) ON DELETE CASCADE,
    front_payload TEXT,
    stay_expires_on DATE,
    card_verified_at TIMESTAMPTZ,
    CONSTRAINT ck_registration_verified CHECK (card_verified_at IS NULL OR (front_payload IS NOT NULL AND stay_expires_on IS NOT NULL))
);
