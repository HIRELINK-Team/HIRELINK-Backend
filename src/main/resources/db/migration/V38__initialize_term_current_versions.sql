-- Initialize the first published terms without changing historical consent records.
UPDATE term_type
SET current_version = 'v1'
WHERE current_version IS NULL;
