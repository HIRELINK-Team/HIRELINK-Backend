-- 인증된 지원 대상 밖의 체류자격도 원문을 보존한다. 가입/직접 입력의 지원 대상 검사는 서비스에서 유지한다.
ALTER TABLE student_profile DROP CONSTRAINT fk_student_profile_visa;

-- 같은 만료일에 대한 재접속/재갱신 안내 중복을 막는다. 기존 회원은 안내 이력 없이 시작한다.
ALTER TABLE student_registration_verification
 ADD COLUMN expiry_notice_dates JSONB NOT NULL DEFAULT '[]'::jsonb,
 ADD CONSTRAINT ck_registration_expiry_notice_dates CHECK (jsonb_typeof(expiry_notice_dates) = 'array');
