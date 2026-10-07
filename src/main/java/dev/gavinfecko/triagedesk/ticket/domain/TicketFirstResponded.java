package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** The first public reply from staff. Stops the first-response SLA timer (TD-42). */
public record TicketFirstResponded(UUID ticketId, String key, UUID agentId, Instant at) {}
