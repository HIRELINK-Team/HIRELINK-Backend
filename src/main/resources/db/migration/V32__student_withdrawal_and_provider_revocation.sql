ALTER TABLE user_account ADD COLUMN withdrawal_requested_at TIMESTAMPTZ,
 ADD COLUMN withdrawal_finalized_at TIMESTAMPTZ, ADD COLUMN account_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_user_withdrawal_due ON user_account(withdrawal_requested_at) WHERE withdrawal_finalized_at IS NULL AND withdrawal_requested_at IS NOT NULL;
ALTER TABLE auth_account ADD COLUMN revocation_token TEXT;
ALTER TABLE social_auth_session ADD COLUMN revocation_token TEXT;
