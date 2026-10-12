-- Menuju Hari — catalog-service — skema awal
-- Kepemilikan data: catalog-service. Read-only bagi service lain (via API).

CREATE TABLE templates (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_templates_code UNIQUE (code)
);

CREATE TABLE template_versions (
    id               UUID         PRIMARY KEY,
    template_id      UUID         NOT NULL REFERENCES templates (id),
    version          VARCHAR(32)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    content_schema   JSONB        NOT NULL,
    theme            JSONB        NOT NULL,
    capability_flags JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_template_version UNIQUE (template_id, version),
    CONSTRAINT ck_template_version_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);

CREATE TABLE packages (
    id           UUID         PRIMARY KEY,
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    price_idr    INT          NOT NULL DEFAULT 0,
    feature_flags JSONB       NOT NULL DEFAULT '{}'::jsonb,
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_packages_code UNIQUE (code)
);

CREATE TABLE music_tracks (
    id               UUID         PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    artist           VARCHAR(200) NOT NULL DEFAULT '',
    object_key       VARCHAR(300) NOT NULL,
    license_note     VARCHAR(500) NOT NULL,
    duration_seconds INT          NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_template_versions_active ON template_versions (template_id, status);
CREATE INDEX idx_music_active ON music_tracks (active);
