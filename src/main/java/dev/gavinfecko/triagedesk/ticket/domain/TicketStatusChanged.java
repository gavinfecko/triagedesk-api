package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** A status transition. The SLA engine pauses and resumes on PENDING (TD-42). */
public record TicketStatusChanged(
        UUID ticketId, String key, TicketStatus from, TicketStatus to, UUID actorId, Instant at) {}
