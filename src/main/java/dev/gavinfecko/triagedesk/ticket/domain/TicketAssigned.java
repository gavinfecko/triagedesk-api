package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The assignee changed (either side may be empty). Notifications tell the new assignee (TD-50). */
public record TicketAssigned(
        UUID ticketId,
        String key,
        @Nullable UUID from,
        @Nullable UUID to,
        UUID actorId,
        Instant at) {}
