-- =====================================================================
-- V10__phase11_interest.sql
-- Phase 11 (F-14) : idempotence du job d interets.
-- =====================================================================

CREATE TABLE interest_run (
    id          UUID PRIMARY KEY,
    run_year    INTEGER NOT NULL,
    run_month   INTEGER NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_interest_run UNIQUE (run_year, run_month)
);

ALTER TABLE financial_transaction ALTER COLUMN initiated_by DROP NOT NULL;

COMMENT ON TABLE interest_run IS 'Idempotence du job d interets - F-14.';
