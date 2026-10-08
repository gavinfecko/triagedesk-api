package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A status transition. The SLA engine pauses and resumes on PENDING (TD-42). */
public record TicketStatusChanged(
        UUID ticketId,
        String key,
        TicketStatus from,
        TicketStatus to,
        @Nullable UUID actorId,
        Instant at) {}
