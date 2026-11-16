ALTER TABLE term_type
    ADD COLUMN is_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_social_signup BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO term_type (term_type_name, is_required, is_social_signup) VALUES
    ('서비스 이용약관', TRUE, TRUE),
    ('개인정보 수집·이용', TRUE, TRUE),
    ('개인정보 제3자 제공', TRUE, TRUE),
    ('마케팅 정보 수신', FALSE, TRUE);

CREATE TABLE social_auth_session_term_agreement (
    token_hash VARCHAR(64) NOT NULL,
    term_type_id BIGINT NOT NULL,
    is_agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMPTZ,
    PRIMARY KEY (token_hash, term_type_id),
    CONSTRAINT fk_social_agreement_session FOREIGN KEY (token_hash)
        REFERENCES social_auth_session(token_hash) ON DELETE CASCADE,
    CONSTRAINT fk_social_agreement_term FOREIGN KEY (term_type_id) REFERENCES term_type(id)
);
