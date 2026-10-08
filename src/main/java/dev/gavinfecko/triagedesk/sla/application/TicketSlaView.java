package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.sla.domain.SlaStatus;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A ticket's two clocks. Either is null only when no active policy existed for its priority at creation. */
public record TicketSlaView(
        String ticketKey,
        @Nullable TimerView firstResponse,
        @Nullable TimerView resolution) {

    public record TimerView(
            UUID id,
            Kind kind,
            SlaStatus status,
            Priority policyPriority,
            UUID calendarId,
            int budgetMinutes,
            Instant startedAt,
            Instant dueAt,
            long remainingBusinessMinutes,
            long pausedTotalMinutes,
            @Nullable Instant pausedAt,
            @Nullable Instant metAt,
            @Nullable Instant breachedAt,
            @Nullable Instant cancelledAt) {}
}
