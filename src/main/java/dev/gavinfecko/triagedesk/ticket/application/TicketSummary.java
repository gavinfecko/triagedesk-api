package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One row of a ticket list: enough to scan and pick, without the description. */
public record TicketSummary(
        UUID id,
        String key,
        String title,
        TicketStatus status,
        Priority priority,
        String category,
        String queue,
        Person requester,
        @Nullable Person assignee,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Sla sla) {

    /** The ticket's current SLA clocks (TD-44); statuses are on_track, at_risk, breached, met, paused, cancelled. */
    public record Sla(
            @Nullable String resolutionStatus,
            @Nullable Instant resolutionDueAt,
            @Nullable String firstResponseStatus) {}
}
