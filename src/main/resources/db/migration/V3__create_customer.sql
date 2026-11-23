-- =====================================================================
-- V3__create_customer.sql
-- Phase 3 (F-02) : table des clients — RG-KYC-01 à 05, §6.1, §7.
--
-- Points pédagogiques (nécessaires pour les entretiens d'embauche) :
-- - clés étrangères systématiques : FK vers app_user (session → données
--   séparées) ; cascade suppression en cas d'effacement de compte ;
-- - VARCHAR + CHECK plutôt qu'enum PostgreSQL natif : portable, et
--   évoluer = déclarer un nouveau cas dans CHECK via une nouvelle
--   migration (jamais modifier V3 une fois commitée) ;
-- - id_doc_number_masked : SEUL le numéro masqué est stocké — RG-KYC-03.
--   Le numéro clair ne vaut jamais en base ni en API ni dans les logs.
-- =====================================================================

CREATE TABLE customer (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    birth_date            DATE NOT NULL,
    phone                 VARCHAR(30) NOT NULL,
    id_doc_type           VARCHAR(20),
    id_doc_number_masked  VARCHAR(20),
    kyc_level             VARCHAR(20) NOT NULL DEFAULT 'LEVEL_1',
    kyc_status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Enuméré fermé — VALUES enumérés dans l'entité (KycLevel)
    CONSTRAINT ck_customer_kyc_level CHECK (kyc_level IN ('LEVEL_1','LEVEL_2','LEVEL_3')),
    -- Enuméré fermé — VALUES enumérés dans l'entité (KycStatus)
    CONSTRAINT ck_customer_kyc_status CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED'))
);

-- Index sur la jointure/filtre fréquent (§6.2) — user_id UNIQUE crée déjà
-- son index, mais on le nomme tout explicitement : lisibilité des plans.
CREATE INDEX idx_customer_user_id ON customer (user_id);

COMMENT ON TABLE customer
    IS 'Clients — profil minimal à l''inscription (RG-KYC-01) + montée de niveau KYC (F-02).';
