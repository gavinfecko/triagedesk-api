package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.sla.domain.SlaTimer;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.sla.infra.SlaTimerRepository;
import dev.gavinfecko.triagedesk.ticket.application.TicketSlaLookup;
import dev.gavinfecko.triagedesk.ticket.application.TicketSummary.Sla;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The SLA columns of a ticket list page: one query for the whole page. */
@Component
class TicketSlaSummaries implements TicketSlaLookup {

    private final SlaTimerRepository timers;
    private final Clock clock;

    TicketSlaSummaries(SlaTimerRepository timers, Clock clock) {
        this.timers = timers;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Sla> forTickets(Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        Map<UUID, List<SlaTimer>> byTicket =
                timers.findByTicketIdIn(ticketIds).stream().collect(Collectors.groupingBy(SlaTimer::ticketId));
        Map<UUID, Sla> result = new HashMap<>();
        byTicket.forEach((ticket, clocks) -> {
            Optional<SlaTimer> resolution = current(clocks, Kind.RESOLUTION);
            Optional<SlaTimer> firstResponse = current(clocks, Kind.FIRST_RESPONSE);
            result.put(
                    ticket,
                    new Sla(
                            resolution.map(t -> t.status(now).json()).orElse(null),
                            resolution.map(SlaTimer::dueAt).orElse(null),
                            firstResponse.map(t -> t.status(now).json()).orElse(null)));
        });
        return result;
    }

    private static Optional<SlaTimer> current(List<SlaTimer> clocks, Kind kind) {
        return clocks.stream().filter(t -> t.kind() == kind).max(Comparator.comparing(SlaTimer::startedAt));
    }
}
