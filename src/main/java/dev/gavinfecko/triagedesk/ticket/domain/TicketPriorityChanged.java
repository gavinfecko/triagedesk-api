package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** The priority was corrected. The SLA engine recalculates the resolution target (TD-42). */
public record TicketPriorityChanged(UUID ticketId, String key, Priority from, Priority to, UUID actorId, Instant at) {}
