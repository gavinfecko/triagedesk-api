package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The priority was corrected, by a person or by SLA escalation (null actor). The SLA engine reschedules its clocks. */
public record TicketPriorityChanged(
        UUID ticketId,
        String key,
        Priority from,
        Priority to,
        @Nullable UUID actorId,
        Instant at) {}
