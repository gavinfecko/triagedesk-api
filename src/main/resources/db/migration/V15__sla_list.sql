-- V15 (TD-44): the ticket list filters and sorts by SLA state, so the state must be computable in SQL.
-- at_risk_at is the instant from which a quarter of the budget or less is left; the application keeps it current
-- when a clock starts, resumes or is rescheduled. Existing rows get a wall-clock approximation.
ALTER TABLE sla_timers ADD COLUMN at_risk_at timestamptz;
UPDATE sla_timers SET at_risk_at = started_at + (due_at - started_at) * 0.75;
ALTER TABLE sla_timers ALTER COLUMN at_risk_at SET NOT NULL;

-- The status of a ticket's current clock of one kind, exactly as SlaTimer.status computes it.
CREATE FUNCTION ticket_sla_status(p_ticket uuid, p_kind text, p_now timestamptz) RETURNS text
LANGUAGE sql STABLE AS $$
    SELECT CASE
               WHEN cancelled_at IS NOT NULL THEN 'cancelled'
               WHEN breached_at IS NOT NULL THEN 'breached'
               WHEN met_at IS NOT NULL AND met_at > due_at THEN 'breached'
               WHEN met_at IS NOT NULL THEN 'met'
               WHEN paused_at IS NOT NULL THEN 'paused'
               WHEN p_now > due_at THEN 'breached'
               WHEN p_now >= at_risk_at THEN 'at_risk'
               ELSE 'on_track'
           END
    FROM sla_timers
    WHERE ticket_id = p_ticket AND kind = p_kind
    ORDER BY started_at DESC
    LIMIT 1
$$;

-- When the ticket's current resolution clock is due, for "soonest due first".
CREATE FUNCTION ticket_sla_due(p_ticket uuid) RETURNS timestamptz
LANGUAGE sql STABLE AS $$
    SELECT due_at FROM sla_timers
    WHERE ticket_id = p_ticket AND kind = 'RESOLUTION'
    ORDER BY started_at DESC
    LIMIT 1
$$;
