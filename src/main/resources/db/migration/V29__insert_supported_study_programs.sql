-- Keep existing program IDs and profile references; do not infer a course for existing members.
INSERT INTO study_program (program_name) VALUES
    ('전문학사'),
    ('학사'),
    ('석사'),
    ('박사'),
    ('교환학생')
ON CONFLICT (program_name) DO NOTHING;
