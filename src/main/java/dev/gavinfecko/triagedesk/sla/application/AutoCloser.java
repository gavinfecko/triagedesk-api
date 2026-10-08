package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.infra.LeaseLock;
import dev.gavinfecko.triagedesk.sla.infra.ResolvedTickets;
import dev.gavinfecko.triagedesk.sla.infra.ResolvedTickets.Candidate;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Closes tickets left RESOLVED for three business days on the calendar their SLA ran on (24x7 when they had no
 * clock). Runs hourly under the same lease mechanism as the breach scan; each close is its own transaction, and a
 * ticket reopened in the meantime is simply not RESOLVED any more.
 */
@Service
public class AutoCloser {

    static final String JOB = "auto-close";
    static final int BUSINESS_DAYS = 3;
    private static final int BATCH = 500;
    private static final Duration LEASE = Duration.ofMinutes(30);
    private static final Logger log = LoggerFactory.getLogger(AutoCloser.class);

    private final ResolvedTickets resolved;
    private final TicketService tickets;
    private final CalendarService calendars;
    private final LeaseLock lease;
    private final JsonMapper json;
    private final Clock clock;
    private final String owner = "auto-close-" + UUID.randomUUID();

    public AutoCloser(
            ResolvedTickets resolved,
            TicketService tickets,
            CalendarService calendars,
            LeaseLock lease,
            JsonMapper json,
            Clock clock) {
        this.resolved = resolved;
        this.tickets = tickets;
        this.calendars = calendars;
        this.lease = lease;
        this.json = json;
        this.clock = clock;
    }

    public record Result(boolean ran, int closed) {}

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void scheduled() {
        Result result = run();
        if (result.closed() > 0) {
            log.info("Auto-close: {} resolved tickets closed", result.closed());
        }
    }

    public Result run() {
        if (!lease.tryAcquire(JOB, owner, LEASE)) {
            log.info("Auto-close skipped: another instance holds the lease");
            return new Result(false, 0);
        }
        try {
            Instant now = clock.instant();
            // Three business days are never shorter than three calendar days, so older rows are the only candidates.
            int closed = 0;
            for (Candidate c : resolved.resolvedBefore(now.minus(Duration.ofDays(BUSINESS_DAYS)), BATCH)) {
                if (!calendarOf(c)
                                .plusBusinessDays(c.resolvedAt(), BUSINESS_DAYS)
                                .isAfter(now)
                        && tickets.closeResolved(c.ticketId())) {
                    closed++;
                }
            }
            return new Result(true, closed);
        } finally {
            lease.release(JOB, owner);
        }
    }

    private BusinessCalendar calendarOf(Candidate c) {
        if (c.policySnapshot() == null) {
            return BusinessCalendar.roundTheClock();
        }
        return calendars.calendar(
                json.readValue(c.policySnapshot(), PolicySnapshot.class).calendarId());
    }
}
