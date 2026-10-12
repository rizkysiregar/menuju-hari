-- Menuju Hari — media-service — skema awal
-- Kepemilikan data: media-service.

CREATE TABLE media_assets (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL,
    invitation_id     UUID,
    kind              VARCHAR(20)  NOT NULL,
    object_key        VARCHAR(300) NOT NULL,
    original_filename VARCHAR(255),
    content_type      VARCHAR(50)  NOT NULL,
    size_bytes        BIGINT       NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    variants          JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_kind CHECK (kind IN ('PHOTO')),
    CONSTRAINT ck_media_status CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX idx_media_user_created ON media_assets (user_id, created_at DESC);
CREATE INDEX idx_media_status_created ON media_assets (status, created_at);
