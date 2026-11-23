-- =====================================================================
-- V5__create_financial_transaction.sql
-- Phase 5 (F-04 — Grand livre) : tables de base du grand livre — RG-LED-01 à 07, §6.1.
--
-- Points pédagogiques (nécessaires pour les entretiens d'embauche) :
-- - immuabilité (RG-LED-03) : triggers PostgreSQL interdisant UPDATE et
--   DELETE sur ledger_entry — "lever une exception explicite" — la
--   correction passe par une contre-passation (F-09 — Phase 6) ;
-- - chaînage de hachages par compte (RG-LED-05) :
--   entry_hash = SHA-256(prev_hash ‖ transaction_id ‖ account_id ‖ direction ‖ amount ‖ currency ‖ created_at)
--   où prev_hash = hash de l'écriture précédente du même compte
--   (valeur initiale : 64 zéros) — calculé en Java dans
--   `LedgerService.computeHash` — parité avec le SQL (l'ordre de
--   concaténation est documenté ici — une altération ferait échouer
--   `GET /admin/ledger/verify`) ;
-- - Σ DEBIT = Σ CREDIT par devise (RG-LED-02) — vérifié en code
--   (`LedgerService.apply`) + test dédié (`CA-LED-01`) ;
-- - verrouillage (RG-LED-06) : `SELECT … FOR UPDATE` sur les comptes en
--   ordre croissant — le test de concurrence (CA-LED-04) ;
-- - balance_after (RG-LED-04) + dénormalisation solde (RG-LED-07) :
--   `account.balance` = Σ crédits − Σ débits — MAJ dans la même
--   transaction — `GET /admin/reconciliation` (F-13 — Phase 9).
-- =====================================================================

CREATE TABLE financial_transaction (
    id                      UUID PRIMARY KEY,
    reference               VARCHAR(20) NOT NULL UNIQUE,                    -- ex. TXN-20261130-000042 (§6.1)
    type                    VARCHAR(20) NOT NULL,                          -- DEPOSIT|WITHDRAWAL|TRANSFER|FX_TRANSFER|EXTERNAL_TRANSFER|FEE|INTEREST|REVERSAL
    status                  VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',      -- PENDING|COMPLETED|FAILED|REVERSED
    amount                  NUMERIC(19,4) NOT NULL,
    fee_amount              NUMERIC(19,4) NOT NULL DEFAULT 0,              -- solde nul — pas de frais par défaut à la Phase 5
    currency                VARCHAR(3) NOT NULL,
    source_account_id       UUID NULL REFERENCES account (id),
    destination_account_id  UUID NULL REFERENCES account (id),
    reversal_of             UUID NULL REFERENCES financial_transaction (id),
    initiated_by            UUID NOT NULL REFERENCES app_user (id),
    description             VARCHAR(500),
    failure_reason          VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,

    -- §6.2 — CHECK
    CONSTRAINT ck_financial_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_financial_transaction_type CHECK (
        type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','FX_TRANSFER','EXTERNAL_TRANSFER','FEE','INTEREST','REVERSAL')),
    CONSTRAINT ck_financial_transaction_status CHECK (status IN ('PENDING','COMPLETED','FAILED','REVERSED'))
);

CREATE INDEX idx_financial_transaction_source ON financial_transaction (source_account_id);
CREATE INDEX idx_financial_transaction_destination ON financial_transaction (destination_account_id);
CREATE INDEX idx_financial_transaction_initiated_by ON financial_transaction (initiated_by);
CREATE INDEX idx_financial_transaction_status ON financial_transaction (status);
CREATE INDEX idx_financial_transaction_reversal_of ON financial_transaction (reversal_of);

CREATE TABLE ledger_entry (
    id                      BIGSERIAL PRIMARY KEY,
    transaction_id          UUID NOT NULL REFERENCES financial_transaction (id),
    account_id              UUID NOT NULL REFERENCES account (id),
    direction               VARCHAR(8) NOT NULL,                           -- DEBIT|CREDIT — « CREDIT » fait 6
    amount                  NUMERIC(19,4) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    balance_after           NUMERIC(19,4) NOT NULL,                        -- RG-LED-04
    prev_hash               VARCHAR(64) NOT NULL,
    entry_hash              VARCHAR(64) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- §6.2 — CHECK
    CONSTRAINT ck_ledger_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT','CREDIT'))
);

CREATE INDEX idx_ledger_entry_account ON ledger_entry (account_id, id);    -- §6.1 — (account_id, id)
CREATE INDEX idx_ledger_entry_transaction ON ledger_entry (transaction_id); -- §6.1

-- ── Triggers d'immuabilité (RG-LED-03 — "aucune écriture est modifiée ni supprimée") ──

CREATE OR REPLACE FUNCTION trg_ledger_entry_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'RG-LED-03 — écriture en partie double immuable — UPDATE/DELETE interdit — correction par contre-passation (F-09)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_update_immutable
    BEFORE UPDATE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION trg_ledger_entry_immutable();

CREATE TRIGGER trg_ledger_entry_delete_immutable
    BEFORE DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION trg_ledger_entry_immutable();

COMMENT ON TABLE financial_transaction
    IS 'Transactions du grand livre — F-04 (§6.1) — status COMPLETED jusqu''au retour de LedgerService.apply (PENDING réservé aux rejets — Phase 6).';
COMMENT ON TABLE ledger_entry
    IS 'Écritures en partie double — immuables (RG-LED-03) — F-04 — Génération des hashes en Java (LedgerService).';
