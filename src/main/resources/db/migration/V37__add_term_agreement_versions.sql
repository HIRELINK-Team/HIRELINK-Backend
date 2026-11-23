-- The initial body version must be coordinated with the frontend; never infer historical consent.
ALTER TABLE term_type ADD COLUMN current_version VARCHAR(64);
ALTER TABLE term_type ADD CONSTRAINT ck_term_current_version
    CHECK (current_version IS NULL OR current_version ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$');
ALTER TABLE user_term_agreement ADD COLUMN term_version VARCHAR(64);
ALTER TABLE social_auth_session_term_agreement ADD COLUMN term_version VARCHAR(64);
