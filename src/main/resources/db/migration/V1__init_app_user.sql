-- =====================================================================
-- V1__init_app_user.sql
-- Phase 0 : première migration. Elle prouve que Flyway fonctionne et
-- pose la table fondatrice du projet (§6.1 du cahier des charges).
--
-- Règle d'or : une migration déjà commitée ne se modifie JAMAIS.
-- Toute correction passe par une nouvelle migration (V2, V3...).
-- =====================================================================

-- Types énumérés : on utilise VARCHAR + CHECK plutôt qu'un enum PostgreSQL
-- natif pour rester portable et simple à faire évoluer (ajouter un rôle
-- = une nouvelle ligne dans le CHECK via migration, sans recréer le type).
CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,          -- 320 = longueur max RFC 5321
    password_hash   VARCHAR(100) NOT NULL,          -- BCrypt coût 12 ≈ 60 chars
    role            VARCHAR(20) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,     -- déclenche le verrou (RG-AUTH-05)
    locked_until    TIMESTAMPTZ,                    -- NULL = non verrouillé
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Contrainte d'unicité : un seul compte par adresse e-mail
    CONSTRAINT uq_app_user_email UNIQUE (email),

    -- Rôles autorisés (matrice §5) — l'arbitrage se fait en base,
    -- pas seulement en Java
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'TELLER', 'ADMIN', 'AUDITOR')),

    -- Un compteur d'échecs ne peut être négatif
    CONSTRAINT ck_app_user_failed_attempts CHECK (failed_attempts >= 0)
);

-- Index sur la colonne de recherche quotidienne (login). L'UNIQUE crée
-- déjà un index pour l'unicité, mais un nom explicite rend le plan de
-- requête lisible et survit à une éventuelle levée d'unicité.
CREATE INDEX idx_app_user_role ON app_user (role);

-- Commentaire de table (visible via \d en psql) : trace du "pourquoi"
COMMENT ON TABLE app_user IS 'Utilisateurs de l''application (F-01). Immuable hors colonnes de verrouillage.';
