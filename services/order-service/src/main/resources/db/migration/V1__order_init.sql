-- Menuju Hari — order-service — skema awal
-- Kepemilikan data: order-service.

CREATE TABLE orders (
    id              UUID         PRIMARY KEY,
    order_number    VARCHAR(64)  NOT NULL,
    user_id         UUID         NOT NULL,
    invitation_id   UUID         NOT NULL,
    customer_email  VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    subtotal_idr    INT          NOT NULL,
    discount_idr    INT          NOT NULL DEFAULT 0,
    total_idr       INT          NOT NULL,
    coupon_code     VARCHAR(64),
    idempotency_key VARCHAR(64),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT uq_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED'))
);

CREATE TABLE order_items (
    id            UUID         PRIMARY KEY,
    order_id      UUID         NOT NULL REFERENCES orders (id),
    kind          VARCHAR(20)  NOT NULL,
    ref_code      VARCHAR(64)  NOT NULL,
    name_snapshot VARCHAR(120) NOT NULL,
    unit_price_idr INT         NOT NULL,
    quantity      INT          NOT NULL DEFAULT 1,
    CONSTRAINT ck_order_item_kind CHECK (kind IN ('PACKAGE', 'ADDON'))
);

CREATE TABLE coupons (
    id                  UUID         PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    kind                VARCHAR(10)  NOT NULL,
    value               INT          NOT NULL,
    max_discount_idr    INT,
    min_transaction_idr INT          NOT NULL DEFAULT 0,
    max_redemptions     INT,
    max_per_user        INT          NOT NULL DEFAULT 1,
    starts_at           TIMESTAMPTZ,
    ends_at             TIMESTAMPTZ,
    allowed_packages    JSONB,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupon_kind CHECK (kind IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_coupon_value CHECK (value > 0)
);

CREATE TABLE coupon_redemptions (
    id            UUID        PRIMARY KEY,
    coupon_id     UUID        NOT NULL REFERENCES coupons (id),
    order_id      UUID        NOT NULL REFERENCES orders (id),
    user_id       UUID        NOT NULL,
    discount_idr  INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id);
