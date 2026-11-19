CREATE TABLE sms_send_history (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    recipient VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    provider_message_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    CONSTRAINT uk_sms_send_history_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_sms_send_history_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
