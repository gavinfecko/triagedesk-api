-- V13 (TD-42): one SLA clock per target per ticket. A timer copies its policy when it starts (policy_snapshot),
-- so later policy edits do not move running clocks. Reopening starts a fresh RESOLUTION timer; old rows stay.
CREATE TABLE sla_timers (
    id                   uuid        PRIMARY KEY,
    ticket_id            uuid        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    kind                 varchar(16) NOT NULL,
    policy_snapshot      jsonb       NOT NULL,
    started_at           timestamptz NOT NULL,
    due_at               timestamptz NOT NULL,
    paused_at            timestamptz,
    paused_total_seconds bigint      NOT NULL DEFAULT 0,
    met_at               timestamptz,
    breached_at          timestamptz,
    cancelled_at         timestamptz,
    CONSTRAINT sla_timers_kind_known CHECK (kind IN ('FIRST_RESPONSE', 'RESOLUTION'))
);
CREATE INDEX sla_timers_ticket_idx ON sla_timers (ticket_id, started_at);
-- The breach scan (TD-43) reads only running clocks: this partial index stays tiny however many tickets close.
CREATE INDEX sla_timers_due_open_idx ON sla_timers (due_at)
    WHERE met_at IS NULL AND breached_at IS NULL AND cancelled_at IS NULL AND paused_at IS NULL;
