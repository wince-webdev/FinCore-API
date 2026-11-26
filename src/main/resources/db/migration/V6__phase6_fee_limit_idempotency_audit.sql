-- =====================================================================
-- V6__phase6_fee_limit_idempotency_audit.sql
-- Phase 6 (F-06, F-09, F-12) : frais, plafonds, idempotence, audit.
-- =====================================================================

CREATE TABLE fee_rule (
    id            UUID PRIMARY KEY,
    operation_type VARCHAR(30) NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    percent_rate  NUMERIC(19,8) NOT NULL DEFAULT 0,
    fixed_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
    min_fee       NUMERIC(19,4) NOT NULL DEFAULT 0,
    max_fee       NUMERIC(19,4) NOT NULL DEFAULT 0,
    valid_from    TIMESTAMPTZ NOT NULL DEFAULT now(),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_fee_rule_currency CHECK (currency IN ('XOF','EUR','USD')),
    CONSTRAINT ck_fee_rule_operation CHECK (operation_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','EXTERNAL_TRANSFER','FX','REVERSAL'))
);
CREATE INDEX idx_fee_rule_lookup ON fee_rule (operation_type, currency, active, valid_from);

CREATE TABLE limit_rule (
    id                  UUID PRIMARY KEY,
    kyc_level           VARCHAR(20) NOT NULL,
    per_transaction_max NUMERIC(19,4),
    daily_outgoing_max  NUMERIC(19,4),
    balance_max         NUMERIC(19,4),
    currency            VARCHAR(3) NOT NULL,
    CONSTRAINT ck_limit_rule_kyc CHECK (kyc_level IN ('LEVEL_1','LEVEL_2','LEVEL_3')),
    CONSTRAINT ck_limit_rule_currency CHECK (currency IN ('XOF','EUR','USD'))
);
CREATE INDEX idx_limit_rule_lookup ON limit_rule (kyc_level, currency);

CREATE TABLE idempotency_record (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(64) NOT NULL,
    user_id          UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint         VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    response_status  INTEGER,
    response_body    JSONB,
    transaction_id   UUID REFERENCES financial_transaction (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS','COMPLETED'))
);
CREATE INDEX idx_idempotency_expires ON idempotency_record (expires_at);

CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id       UUID,
    actor_role     VARCHAR(20),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(50),
    entity_id      VARCHAR(100),
    ip_address      VARCHAR(45),
    correlation_id VARCHAR(100),
    details        JSONB
);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id);
CREATE INDEX idx_audit_log_action ON audit_log (action);
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at);

CREATE OR REPLACE FUNCTION trg_audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log immuable - UPDATE/DELETE interdit';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_update_immutable
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION trg_audit_log_immutable();

CREATE TRIGGER trg_audit_log_delete_immutable
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION trg_audit_log_immutable();

-- Seed fee rules
INSERT INTO fee_rule (id, operation_type, currency, percent_rate, fixed_amount, min_fee, max_fee, valid_from, active) VALUES
 (gen_random_uuid(),'DEPOSIT','XOF',0,0,0,0,now(),TRUE),
 (gen_random_uuid(),'DEPOSIT','EUR',0,0,0,0,now(),TRUE),
 (gen_random_uuid(),'DEPOSIT','USD',0,0,0,0,now(),TRUE),
 (gen_random_uuid(),'WITHDRAWAL','XOF',0.003,0,100,3000,now(),TRUE),
 (gen_random_uuid(),'WITHDRAWAL','EUR',0.003,0,0.15,4.57,now(),TRUE),
 (gen_random_uuid(),'WITHDRAWAL','USD',0.003,0,0.15,4.57,now(),TRUE),
 (gen_random_uuid(),'TRANSFER','XOF',0.005,0,100,2000,now(),TRUE),
 (gen_random_uuid(),'TRANSFER','EUR',0.005,0,0.15,3.05,now(),TRUE),
 (gen_random_uuid(),'TRANSFER','USD',0.005,0,0.15,3.05,now(),TRUE),
 (gen_random_uuid(),'EXTERNAL_TRANSFER','XOF',0.015,200,300,10000,now(),TRUE),
 (gen_random_uuid(),'EXTERNAL_TRANSFER','EUR',0.015,0.30,0.46,15.24,now(),TRUE),
 (gen_random_uuid(),'EXTERNAL_TRANSFER','USD',0.015,0.30,0.46,15.24,now(),TRUE);

-- Seed limit rules
INSERT INTO limit_rule (id, kyc_level, per_transaction_max, daily_outgoing_max, balance_max, currency) VALUES
 (gen_random_uuid(),'LEVEL_1',100000,200000,500000,'XOF'),
 (gen_random_uuid(),'LEVEL_2',2000000,5000000,20000000,'XOF'),
 (gen_random_uuid(),'LEVEL_3',10000000,20000000,NULL,'XOF'),
 (gen_random_uuid(),'LEVEL_1',152.45,304.90,762.24,'EUR'),
 (gen_random_uuid(),'LEVEL_2',3048.91,7622.28,30489.12,'EUR'),
 (gen_random_uuid(),'LEVEL_3',15244.55,30489.10,NULL,'EUR'),
 (gen_random_uuid(),'LEVEL_1',108.11,216.22,540.54,'USD'),
 (gen_random_uuid(),'LEVEL_2',2162.16,5405.41,21621.62,'USD'),
 (gen_random_uuid(),'LEVEL_3',10810.81,21621.62,NULL,'USD');

COMMENT ON TABLE fee_rule IS 'Barème de frais - F-06/F-13.';
COMMENT ON TABLE limit_rule IS 'Plafonds par niveau KYC - F-06.';
COMMENT ON TABLE idempotency_record IS 'Idempotence des opérations financières - RG-IDEM.';
COMMENT ON TABLE audit_log IS 'Piste d audit immuable - F-12.';
