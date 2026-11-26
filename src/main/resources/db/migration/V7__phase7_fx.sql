-- =====================================================================
-- V7__phase7_fx.sql
-- Phase 7 (F-07) : taux de change + colonnes FX sur financial_transaction.
-- =====================================================================

ALTER TABLE financial_transaction
    ADD COLUMN destination_amount NUMERIC(19,4),
    ADD COLUMN exchange_rate NUMERIC(19,8),
    ADD COLUMN spread_percent NUMERIC(19,4);

CREATE TABLE fx_rate (
    id             UUID PRIMARY KEY,
    base_currency  VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate           NUMERIC(19,8) NOT NULL,
    spread_percent NUMERIC(19,4) NOT NULL DEFAULT 1.0,
    valid_from     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source         VARCHAR(50)
);
CREATE INDEX idx_fx_rate_lookup ON fx_rate (base_currency, quote_currency, valid_from);

INSERT INTO fx_rate (id, base_currency, quote_currency, rate, spread_percent, valid_from, source) VALUES
 (gen_random_uuid(),'EUR','XOF',655.957,1.0,now(),'FIXED'),
 (gen_random_uuid(),'XOF','EUR',0.00152439,1.0,now(),'FIXED'),
 (gen_random_uuid(),'USD','XOF',615.00,1.0,now(),'PARAM'),
 (gen_random_uuid(),'XOF','USD',0.00162602,1.0,now(),'PARAM'),
 (gen_random_uuid(),'EUR','USD',1.08,1.0,now(),'PARAM'),
 (gen_random_uuid(),'USD','EUR',0.92592593,1.0,now(),'PARAM');

COMMENT ON TABLE fx_rate IS 'Taux de change - F-07.';
