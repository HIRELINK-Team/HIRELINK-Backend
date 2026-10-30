CREATE TABLE employer_profile (
    id BIGINT PRIMARY KEY,
    position VARCHAR(50),
    CONSTRAINT fk_employer_profile_user FOREIGN KEY (id) REFERENCES user_account(id)
);
