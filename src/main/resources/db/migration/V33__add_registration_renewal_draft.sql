ALTER TABLE student_registration_verification
 ADD COLUMN renewal_id UUID,
 ADD COLUMN renewal_expires_at TIMESTAMPTZ,
 ADD COLUMN renewal_payload TEXT,
 ADD COLUMN renewal_card_expires_on DATE,
 ADD COLUMN renewal_card_verified_at TIMESTAMPTZ,
 ADD COLUMN renewal_document_expires_on DATE,
 ADD COLUMN renewal_document_verified_at TIMESTAMPTZ;
ALTER TABLE student_registration_verification ADD CONSTRAINT ck_registration_renewal_window
 CHECK ((renewal_id IS NULL) = (renewal_expires_at IS NULL));
CREATE INDEX ix_registration_renewal_expiry ON student_registration_verification(renewal_expires_at)
 WHERE renewal_id IS NOT NULL;
