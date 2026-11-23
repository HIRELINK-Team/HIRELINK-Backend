ALTER TABLE refresh_token
    ADD COLUMN session_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uk_refresh_token_session UNIQUE (session_id);
