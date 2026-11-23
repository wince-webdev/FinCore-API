-- =====================================================================
-- V2__create_refresh_token.sql
-- Phase 2 (F-01) : table des refresh tokens (RG-AUTH-03, RG-AUTH-04).
--
-- Rappel §6.1 : id UUID PK, user_id FK, token_hash UNIQUE, expires_at,
-- revoked_at, replaced_by, created_at.
--
-- Pourquoi stocker un HASH et pas le token en clair ?
-- Une base volée ne doit pas permettre de se connecter : on ne stocke
-- que le SHA-256 (64 hex). Le token opaque fait 256 bits d'entropie —
-- indevinable — donc un hash NON salé est suffisant (contrairement à
-- un mot de passe) et permet un lookup direct par index UNIQUE.
--
-- Règle d'or : une migration commitée ne se modifie jamais.
-- =====================================================================

CREATE TABLE refresh_token (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL,             -- SHA-256 hex = 64 chars
    expires_at    TIMESTAMPTZ NOT NULL,             -- validité 7 jours (RG-AUTH-03)
    revoked_at    TIMESTAMPTZ,                      -- NULL = encore valide
    replaced_by   UUID REFERENCES refresh_token (id), -- rotation : le successeur
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),

    -- Un hash de token ne peut jamais être vide
    CONSTRAINT ck_refresh_token_hash CHECK (length(token_hash) = 64)
);

-- Lookup par utilisateur (révocation de famille) et par hash (refresh).
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- Les tokens encore actifs (ceux que la logique de rotation consulte).
CREATE INDEX idx_refresh_token_active
    ON refresh_token (user_id, expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_token IS
    'Refresh tokens opaques hachés — rotation et révocation de famille (F-01).';
