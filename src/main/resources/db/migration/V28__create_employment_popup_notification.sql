CREATE TABLE employment_popup_notification (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES user_account(id),
    notification_type VARCHAR(40) NOT NULL,
    application_id BIGINT REFERENCES application(id),
    employment_offer_id BIGINT REFERENCES employment_offer(id),
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT ck_employment_popup_notification_type CHECK (notification_type IN (
        'APPLICATION_CREATED',
        'APPLICATION_REJECTED',
        'OFFER_CREATED',
        'OFFER_ACCEPTED',
        'OFFER_REJECTED'
    )),
    CONSTRAINT ck_employment_popup_notification_reference CHECK (
        (notification_type IN ('APPLICATION_CREATED', 'APPLICATION_REJECTED')
            AND application_id IS NOT NULL AND employment_offer_id IS NULL)
        OR (notification_type IN ('OFFER_CREATED', 'OFFER_ACCEPTED', 'OFFER_REJECTED')
            AND application_id IS NULL AND employment_offer_id IS NOT NULL)
    ),
    CONSTRAINT uk_employment_popup_notification_application
        UNIQUE (notification_type, application_id, recipient_user_id),
    CONSTRAINT uk_employment_popup_notification_offer
        UNIQUE (notification_type, employment_offer_id, recipient_user_id)
);

CREATE INDEX idx_employment_popup_notification_unconfirmed
    ON employment_popup_notification(recipient_user_id, created_at, id)
    WHERE confirmed_at IS NULL;
