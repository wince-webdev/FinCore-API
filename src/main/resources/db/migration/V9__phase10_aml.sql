-- =====================================================================
-- V9__phase10_aml.sql
-- Phase 10 (F-11) : alertes AML.
-- =====================================================================

CREATE TABLE aml_alert (
    id            UUID PRIMARY KEY,
    rule_code     VARCHAR(20) NOT NULL,
    severity      VARCHAR(10) NOT NULL,
    customer_id   UUID,
    transaction_id UUID REFERENCES financial_transaction (id),
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    details       JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by   UUID REFERENCES app_user (id),
    reviewed_at   TIMESTAMPTZ,
    CONSTRAINT ck_aml_severity CHECK (severity IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_aml_status CHECK (status IN ('OPEN','REVIEWED','CLOSED'))
);
CREATE INDEX idx_aml_customer ON aml_alert (customer_id);
CREATE INDEX idx_aml_status ON aml_alert (status);

COMMENT ON TABLE aml_alert IS 'Alertes AML - F-11.';
