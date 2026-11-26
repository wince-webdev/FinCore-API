-- =====================================================================
-- V8__phase8_outbox.sql
-- Phase 8 (F-08) : outbox transactionnelle + machine a etats externe.
-- =====================================================================

CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW','SENT','FAILED'))
);
CREATE INDEX idx_outbox_status_next ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_outbox_aggregate ON outbox_event (aggregate_type, aggregate_id);

COMMENT ON TABLE outbox_event IS 'Outbox transactionnelle - F-08.';
