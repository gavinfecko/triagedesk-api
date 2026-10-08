-- V12 (TD-40): one SLA policy per priority. Timers (TD-42) copy a policy's budgets when a ticket is created,
-- so editing a policy changes only the tickets opened after the edit.
CREATE TABLE sla_policies (
    id                     uuid        PRIMARY KEY,
    priority               varchar(16) NOT NULL,
    first_response_minutes integer     NOT NULL,
    resolution_minutes     integer     NOT NULL,
    calendar_id            uuid        NOT NULL REFERENCES business_calendars (id),
    active                 boolean     NOT NULL DEFAULT true,
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT sla_policies_priority_unique UNIQUE (priority),
    CONSTRAINT sla_policies_priority_known CHECK (priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW')),
    CONSTRAINT sla_policies_first_response_positive CHECK (first_response_minutes >= 1),
    CONSTRAINT sla_policies_resolution_after_first_response CHECK (resolution_minutes > first_response_minutes)
);
