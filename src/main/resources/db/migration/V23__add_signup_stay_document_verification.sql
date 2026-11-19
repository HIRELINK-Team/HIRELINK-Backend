ALTER TABLE social_signup_registration
    ADD COLUMN document_expires_on DATE,
    ADD COLUMN document_verified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_registration_document_verified CHECK (
        (document_verified_at IS NULL AND document_expires_on IS NULL)
        OR (document_verified_at IS NOT NULL AND document_expires_on IS NOT NULL AND card_verified_at IS NOT NULL)
    );
