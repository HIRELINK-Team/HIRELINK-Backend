ALTER TABLE employment_match
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX ix_employment_match_active_completed
    ON employment_match (completed_at DESC, id DESC)
    WHERE deleted_at IS NULL;
