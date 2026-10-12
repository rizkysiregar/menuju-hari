-- Menuju Hari — invitation-service — skema awal
-- Kepemilikan data: invitation-service. Tidak ada service lain yang boleh
-- mengakses tabel ini (order-service hanya menyimpan invitation_id sebagai referensi).

CREATE TABLE invitations (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,
    template_code    VARCHAR(64)  NOT NULL,
    template_version VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    slug             VARCHAR(160) NOT NULL,
    bride_name       VARCHAR(100) NOT NULL,
    groom_name       VARCHAR(100) NOT NULL,
    event_date       DATE         NOT NULL,
    content          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    content_version  INT          NOT NULL DEFAULT 1,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    archived_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_invitation_status CHECK (status IN ('DRAFT', 'ELIGIBLE', 'PUBLISHED', 'EXPIRED', 'ARCHIVED', 'HIDDEN'))
);

CREATE UNIQUE INDEX uq_invitations_slug ON invitations (slug);
CREATE INDEX idx_invitations_user_created ON invitations (user_id, created_at DESC);
