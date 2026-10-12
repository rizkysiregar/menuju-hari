-- Menuju Hari — payment-service — skema awal
-- Kepemilikan data: payment-service.

CREATE TABLE payments (
    id               UUID         PRIMARY KEY,
    order_number     VARCHAR(64)  NOT NULL,
    gateway_order_id VARCHAR(64)  NOT NULL,
    attempt          INT          NOT NULL DEFAULT 1,
    gateway          VARCHAR(20)  NOT NULL DEFAULT 'MIDTRANS',
    token            VARCHAR(255),
    redirect_url     VARCHAR(500),
    gross_amount_idr INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_type     VARCHAR(40),
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_gateway_order UNIQUE (gateway_order_id),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SETTLED', 'DENIED', 'CANCELLED', 'EXPIRED', 'REFUNDED'))
);

CREATE TABLE payment_events (
    id               UUID         PRIMARY KEY,
    gateway_order_id VARCHAR(64)  NOT NULL,
    order_number     VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    status_code      VARCHAR(10),
    fraud_status     VARCHAR(20),
    gross_amount     VARCHAR(40),
    signature_valid  BOOLEAN      NOT NULL DEFAULT FALSE,
    payload          JSONB        NOT NULL,
    processed        BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order ON payments (order_number);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
CREATE INDEX idx_events_order ON payment_events (gateway_order_id, event_type, status_code);
