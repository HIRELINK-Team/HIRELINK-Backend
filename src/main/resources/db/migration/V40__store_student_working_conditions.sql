ALTER TABLE student_profile
    ADD COLUMN working_conditions JSONB;

COMMENT ON COLUMN student_profile.working_conditions IS
    'Final-document-based working condition estimate; presence does not grant access or employment permission';
