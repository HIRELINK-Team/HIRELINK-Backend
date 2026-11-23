ALTER TABLE user_account
    ADD COLUMN withdrawal_retain_until TIMESTAMPTZ,
    ADD COLUMN retained_data_purged_at TIMESTAMPTZ;

UPDATE user_account
SET withdrawal_retain_until = ((withdrawal_finalized_at AT TIME ZONE 'Asia/Seoul') + INTERVAL '3 years') AT TIME ZONE 'Asia/Seoul'
WHERE withdrawal_finalized_at IS NOT NULL;

CREATE INDEX ix_user_withdrawal_retention_due ON user_account(withdrawal_retain_until)
    WHERE withdrawal_retain_until IS NOT NULL AND retained_data_purged_at IS NULL;

ALTER TABLE user_account DROP CONSTRAINT uk_user_account_phone_number;
CREATE UNIQUE INDEX uk_user_account_phone_number ON user_account(phone_number)
    WHERE withdrawal_finalized_at IS NULL;

ALTER TABLE user_account ALTER COLUMN preferred_language DROP NOT NULL, ALTER COLUMN created_at DROP NOT NULL;
ALTER TABLE user_account ADD CONSTRAINT ck_user_retention_state CHECK (
    (withdrawal_finalized_at IS NULL AND withdrawal_retain_until IS NULL AND retained_data_purged_at IS NULL)
    OR (withdrawal_finalized_at IS NOT NULL AND withdrawal_retain_until IS NOT NULL
        AND withdrawn_at IS NOT NULL AND withdrawal_retain_until > withdrawal_finalized_at
        AND (retained_data_purged_at IS NULL OR retained_data_purged_at >= withdrawal_retain_until)));
ALTER TABLE user_account ADD CONSTRAINT ck_user_retained_fields CHECK (
    (retained_data_purged_at IS NULL AND preferred_language IS NOT NULL AND created_at IS NOT NULL)
    OR (retained_data_purged_at IS NOT NULL AND name IS NULL AND email IS NULL AND email_verified_at IS NULL
        AND phone_number IS NULL AND phone_verified_at IS NULL AND acquisition_channel IS NULL
        AND preferred_language IS NULL AND created_at IS NULL));

ALTER TABLE student_enrollment_document ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE student_enrollment_document DROP CONSTRAINT ck_student_document_registered;
ALTER TABLE student_enrollment_document ADD CONSTRAINT ck_student_document_registered CHECK (
    (deleted_at IS NULL AND object_key IS NULL AND registered_at IS NULL)
    OR (registered_at IS NOT NULL
        AND ((deleted_at IS NULL AND object_key IS NOT NULL)
            OR (deleted_at IS NOT NULL AND object_key IS NULL))
        AND ((document_type='ENROLLMENT' AND grade_level IS NOT NULL AND university_name IS NOT NULL)
            OR (document_type='TRANSCRIPT' AND average_gpa IS NOT NULL AND average_gpa >= 0 AND academic_year IS NOT NULL AND semester IS NOT NULL)
            OR (document_type='TOPIK' AND topik_level IS NOT NULL AND topik_level BETWEEN 1 AND 6))));
ALTER TABLE student_enrollment_document ADD CONSTRAINT ck_deleted_document_draft CHECK (
    deleted_at IS NULL OR (draft_id IS NULL AND draft_object_key IS NULL AND draft_university_name IS NULL
        AND draft_campus_name IS NULL AND draft_grade_level IS NULL AND draft_semester_count IS NULL
        AND draft_average_gpa IS NULL AND draft_topik_level IS NULL AND draft_academic_year IS NULL AND draft_semester IS NULL));

ALTER TABLE student_profile ADD COLUMN retained_data_purged_at TIMESTAMPTZ;
ALTER TABLE student_profile ALTER COLUMN birth_date DROP NOT NULL, ALTER COLUMN nationality DROP NOT NULL,
    ALTER COLUMN current_region DROP NOT NULL, ALTER COLUMN korean_level DROP NOT NULL;
ALTER TABLE student_profile ADD CONSTRAINT ck_profile_retained_fields CHECK (
    (retained_data_purged_at IS NULL AND birth_date IS NOT NULL AND nationality IS NOT NULL
        AND current_region IS NOT NULL AND korean_level IS NOT NULL)
    OR (retained_data_purged_at IS NOT NULL AND birth_date IS NULL AND nationality IS NULL AND current_region IS NULL
        AND korean_level IS NULL AND visa_code IS NULL AND university_name IS NULL AND enrollment_start_date IS NULL
        AND study_program_id IS NULL AND grade_level IS NULL AND academic_state IS NULL AND topik_level IS NULL
        AND stay_start_date IS NULL AND stay_expiration_date IS NULL AND profile_image_key IS NULL
        AND certification_prompt_dismissed_at IS NULL AND hirelink_badge_issued_at IS NULL
        AND onboarding_completed_at IS NULL AND previous_semester_gpa IS NULL AND working_conditions IS NULL));
