-- Menuju Hari — identity-service — skema awal
-- Kepemilikan data: identity-service. Tidak ada service lain yang boleh mengakses tabel ini.

CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- Unik per email (case-insensitive); PostgreSQL: constraint UNIQUE tidak
-- mendukung ekspresi, jadi dipakai unique index eksplisit.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE otp_challenges (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,          -- SHA-256(salt || kode), hex
    salt       VARCHAR(32)  NOT NULL,          -- 16 byte acak, hex
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    attempts   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_otp_status CHECK (status IN ('ACTIVE', 'VERIFIED', 'EXPIRED', 'EXHAUSTED'))
);

CREATE INDEX idx_otp_email_status_created ON otp_challenges (email, status, created_at DESC);
CREATE INDEX idx_otp_expires ON otp_challenges (expires_at);
