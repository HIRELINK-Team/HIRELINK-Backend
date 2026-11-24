ALTER TABLE employer_profile ADD COLUMN onboarding_completed_at TIMESTAMPTZ;
-- 이미 프로필과 사업장을 가진 사업주는 다시 온보딩하지 않는다.
UPDATE employer_profile p SET onboarding_completed_at = CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM workplace w WHERE w.employer_user_id = p.id);

CREATE TABLE employer_onboarding_attempt (
    -- Short-lived receipts are validated against the live account and login session by the service.
    user_id BIGINT PRIMARY KEY,
    attempt_id UUID NOT NULL UNIQUE,
    login_session_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ocr_number VARCHAR(10),
    ocr_completed_at TIMESTAMPTZ,
    verified_business_number VARCHAR(10),
    business_verified_at TIMESTAMPTZ
);
CREATE INDEX idx_employer_onboarding_attempt_expiry ON employer_onboarding_attempt(expires_at);

-- 기존 표시용 전체 주소는 보존하고, 새 입력부터 기본·상세 주소를 함께 저장한다.
ALTER TABLE workplace ADD COLUMN workplace_base_address VARCHAR(500);
ALTER TABLE workplace ADD COLUMN workplace_address_detail VARCHAR(500);
ALTER TABLE workplace ALTER COLUMN workplace_address TYPE VARCHAR(1001);
