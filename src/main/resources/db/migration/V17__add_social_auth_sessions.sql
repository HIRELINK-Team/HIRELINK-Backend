ALTER TABLE user_account ALTER COLUMN email DROP NOT NULL;
ALTER TABLE user_account DROP CONSTRAINT uk_user_account_email;

CREATE TABLE social_auth_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_social_auth_session_expires_at ON social_auth_session (expires_at);
