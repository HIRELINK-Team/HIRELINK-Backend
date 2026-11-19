CREATE TABLE student_registration_verification (
    user_id BIGINT PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    nationality VARCHAR(2) NOT NULL,
    visa_code VARCHAR(20) NOT NULL REFERENCES visa_type(visa_code),
    stay_expires_on DATE NOT NULL,
    card_verified_at TIMESTAMPTZ NOT NULL,
    document_verified_at TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL
);
