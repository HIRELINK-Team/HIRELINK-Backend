ALTER TABLE phone_verification ADD COLUMN verified_at TIMESTAMPTZ;
ALTER TABLE phone_verification ADD COLUMN change_expires_at TIMESTAMPTZ;
ALTER TABLE phone_verification ADD CONSTRAINT ck_phone_change_confirmation
CHECK ((verified_at IS NULL AND change_expires_at IS NULL)
    OR (user_id IS NOT NULL AND verified_at IS NOT NULL AND change_expires_at IS NOT NULL AND change_expires_at > verified_at));
