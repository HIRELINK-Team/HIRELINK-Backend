CREATE TABLE student_enrollment_document (
    user_id BIGINT PRIMARY KEY REFERENCES student_profile(id),
    draft_id UUID,
    draft_object_key VARCHAR(500),
    draft_university_name VARCHAR(150),
    draft_campus_name VARCHAR(150),
    draft_grade_level INTEGER,
    draft_semester_count INTEGER,
    object_key VARCHAR(500),
    university_name VARCHAR(150),
    campus_name VARCHAR(150),
    grade_level SMALLINT CHECK (grade_level BETWEEN 1 AND 4),
    registered_at TIMESTAMPTZ,
    CONSTRAINT ck_enrollment_draft CHECK ((draft_id IS NULL) = (draft_object_key IS NULL)),
    CONSTRAINT ck_enrollment_registered CHECK (
        (object_key IS NULL AND registered_at IS NULL AND grade_level IS NULL)
        OR (object_key IS NOT NULL AND registered_at IS NOT NULL AND grade_level IS NOT NULL AND university_name IS NOT NULL))
);

CREATE TABLE student_document_cleanup (
    object_key VARCHAR(500) PRIMARY KEY,
    available_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_student_document_cleanup_available ON student_document_cleanup(available_at);
