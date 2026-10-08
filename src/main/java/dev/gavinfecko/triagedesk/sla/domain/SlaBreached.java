package dev.gavinfecko.triagedesk.sla.domain;

import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A clock ran out. The assignee is emailed (TD-50); {@code escalatedTo} is set if the priority rose. */
public record SlaBreached(
        UUID ticketId,
        Kind kind,
        Instant dueAt,
        Instant breachedAt,
        @Nullable Priority escalatedTo) {}
