ALTER TABLE student_enrollment_document ADD COLUMN document_type VARCHAR(20) NOT NULL DEFAULT 'ENROLLMENT';
ALTER TABLE student_enrollment_document DROP CONSTRAINT student_enrollment_document_pkey;
ALTER TABLE student_enrollment_document ADD PRIMARY KEY (user_id, document_type);
ALTER TABLE student_enrollment_document ADD CONSTRAINT ck_student_document_type CHECK (document_type IN ('ENROLLMENT','TRANSCRIPT','TOPIK'));
ALTER TABLE student_enrollment_document ADD COLUMN draft_average_gpa NUMERIC(10,4), ADD COLUMN average_gpa NUMERIC(10,4),
 ADD COLUMN draft_topik_level SMALLINT, ADD COLUMN topik_level SMALLINT,
 ADD COLUMN draft_academic_year INTEGER, ADD COLUMN academic_year INTEGER,
 ADD COLUMN draft_semester VARCHAR(10), ADD COLUMN semester VARCHAR(10);
ALTER TABLE student_enrollment_document DROP CONSTRAINT ck_enrollment_registered;
ALTER TABLE student_enrollment_document ADD CONSTRAINT ck_student_document_registered CHECK (
 (object_key IS NULL AND registered_at IS NULL) OR
 (object_key IS NOT NULL AND registered_at IS NOT NULL AND
 ((document_type='ENROLLMENT' AND grade_level IS NOT NULL AND university_name IS NOT NULL)
 OR (document_type='TRANSCRIPT' AND average_gpa IS NOT NULL AND average_gpa >= 0 AND academic_year IS NOT NULL AND semester IS NOT NULL)
 OR (document_type='TOPIK' AND topik_level IS NOT NULL AND topik_level BETWEEN 1 AND 6))));
ALTER TABLE student_profile ADD COLUMN previous_semester_gpa NUMERIC(10,4) CHECK (previous_semester_gpa >= 0);
