-- =====================================================================
-- V4__create_account.sql
-- Phase 4 (F-03) : comptes clients + comptes système — RG-ACC-01 à 07, §6.1.
--
-- Points pédagogiques (nécessaires pour les entretiens d'embauche) :
-- - UNIQUE PARTIEL sur (customer_id, kind, currency) — RG-ACC-02 (un seul
--   compte par couple type/devise) — l'unicité sur les NULL PostgreSQL
--   ne couvrirait pas les systèmes — indices partiels beaucoup plus
--   lisibles dans les plans de requêtes que des fonctions de colonne ;
-- - seed des comptes systèmes (RG-ACC-06) — 6 codes × 3 devises —
--   numéros générés avec la clé mod 97 de AccountNumberGenerator (§6.1) :
--   le numéro = 'BJ' || 10 chiffres || clé où clé = 97 − (n mod 97) —
--   ici calculé directement en SQL (n = le nombre de 10 chiffres) ;
-- - VARCHAR + CHECK plutôt qu'enum PostgreSQL natif (portabilité,
--   voir V3) ;
-- - account_number VARCHAR(14) : BJ (2) + 10 (corps) + 2 (clé).
-- =====================================================================

CREATE TABLE account (
    id                    UUID PRIMARY KEY,
    account_number        VARCHAR(14) NOT NULL UNIQUE,
    customer_id           UUID NULL REFERENCES customer (id) ON DELETE CASCADE,
    kind                  VARCHAR(20) NOT NULL,
    system_code           VARCHAR(20),
    currency              VARCHAR(3) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    balance               NUMERIC(19,4) NOT NULL DEFAULT 0,
    version               BIGINT NOT NULL DEFAULT 0,
    opened_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at             TIMESTAMPTZ NULL,

    -- Enuméré fermé — VALUES enumérés dans l'entité (AccountKind)
    CONSTRAINT ck_account_kind CHECK (kind IN ('CURRENT','SAVINGS','WALLET','SYSTEM')),
    -- Enuméré fermé — VALUES enumérés dans l'entité (SystemCode)
    CONSTRAINT ck_account_system_code CHECK (system_code IS NULL OR system_code IN ('CASH_VAULT','FEE_REVENUE','FX_POSITION','EXTERNAL_SETTLEMENT','SUSPENSE','INTEREST_EXPENSE')),
    -- RG-ACC-01 — devises supportées
    CONSTRAINT ck_account_currency CHECK (currency IN ('XOF','EUR','USD')),
    -- RG-ACC-04 — statuts
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);

-- Index sur la jointure/filtre fréquent (§6.2) — customer_id NULL pour les
-- systèmes — les comptes clients uniques sur le couple (customer_id, kind, currency).
CREATE INDEX idx_account_customer_id ON account (customer_id);

-- RG-ACC-02 : un seul compte par couple (type, devise) — partiel car
-- account_number UNIQUE autoriserait plusieurs NULL — les comptes systèmes
-- n'ont pas de customer_id.
CREATE UNIQUE INDEX uq_account_customer_kind_currency
    ON account (customer_id, kind, currency)
    WHERE customer_id IS NOT NULL;

-- RG-ACC-06 : seed des comptes systèmes — 6 codes × 3 devises (XOF/EUR/USD).
-- 'BJ' || body || lpad(97 − (body % 97)) — conforme à AccountNumberGenerator.
-- body ∈ [1000000000, 9999999999] — 10 chiffres — clé = 97 − (n mod 97) ∈ 1..97.
-- (L'opérateur % PostgreSQL est défini sur bigint — plus simple que mod().)
WITH pairs(code, kind, ccy) AS (
    VALUES
        ('CASH_VAULT', 'SYSTEM', 'XOF'), ('CASH_VAULT', 'SYSTEM', 'EUR'), ('CASH_VAULT', 'SYSTEM', 'USD'),
        ('FEE_REVENUE', 'SYSTEM', 'XOF'), ('FEE_REVENUE', 'SYSTEM', 'EUR'), ('FEE_REVENUE', 'SYSTEM', 'USD'),
        ('FX_POSITION', 'SYSTEM', 'XOF'), ('FX_POSITION', 'SYSTEM', 'EUR'), ('FX_POSITION', 'SYSTEM', 'USD'),
        ('EXTERNAL_SETTLEMENT', 'SYSTEM', 'XOF'), ('EXTERNAL_SETTLEMENT', 'SYSTEM', 'EUR'), ('EXTERNAL_SETTLEMENT', 'SYSTEM', 'USD'),
        ('SUSPENSE', 'SYSTEM', 'XOF'), ('SUSPENSE', 'SYSTEM', 'EUR'), ('SUSPENSE', 'SYSTEM', 'USD'),
        ('INTEREST_EXPENSE', 'SYSTEM', 'XOF'), ('INTEREST_EXPENSE', 'SYSTEM', 'EUR'), ('INTEREST_EXPENSE', 'SYSTEM', 'USD')
),
gen AS (
    SELECT code, kind, ccy, floor(random() * 9000000000 + 1000000000)::bigint AS body
    FROM pairs
)
INSERT INTO account (id, account_number, kind, system_code, currency, status, balance, version, opened_at)
SELECT gen_random_uuid(),
       'BJ' || lpad(body::text, 10, '0') || lpad((97 - (body % 97::bigint))::text, 2, '0'),
       kind,
       code,
       ccy,
       'ACTIVE',
       0,
       0,
       now()
FROM gen;

COMMENT ON TABLE account
    IS 'Comptes — clients (ouverture F-03) + comptes systèmes (RG-ACC-06) — §6.1.';
