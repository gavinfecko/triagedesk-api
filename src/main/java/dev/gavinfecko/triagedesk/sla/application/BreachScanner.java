package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.sla.domain.SlaBreached;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.sla.infra.LeaseLock;
import dev.gavinfecko.triagedesk.sla.infra.SlaTimerRepository;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds clocks that ran out, once a minute. Each batch of 100 is one transaction: mark breached, audit as the
 * system, escalate P3/P4 resolution breaches one priority level, publish {@link SlaBreached}. A breached clock
 * is never selected again, and a lease keeps a second instance from scanning at the same time.
 */
@Service
public class BreachScanner {

    static final String JOB = "sla-breach-scan";
    static final int BATCH = 100;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Set<Priority> ESCALATE = Set.of(Priority.P3_MEDIUM, Priority.P4_LOW);
    private static final Logger log = LoggerFactory.getLogger(BreachScanner.class);

    private final SlaTimerRepository timers;
    private final TicketService tickets;
    private final LeaseLock lease;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final JsonMapper json;
    private final Clock clock;
    private final Timer duration;
    private final String owner = "scanner-" + UUID.randomUUID();

    public BreachScanner(
            SlaTimerRepository timers,
            TicketService tickets,
            LeaseLock lease,
            AuditLog audit,
            ApplicationEventPublisher events,
            TransactionTemplate transactions,
            JsonMapper json,
            Clock clock,
            MeterRegistry meters) {
        this.timers = timers;
        this.tickets = tickets;
        this.lease = lease;
        this.audit = audit;
        this.events = events;
        this.transactions = transactions;
        this.json = json;
        this.clock = clock;
        this.duration = Timer.builder("sla.scan")
                .description("One breach scan, lease to release")
                .register(meters);
    }

    public record Result(boolean ran, int breached, int escalated) {
        static final Result SKIPPED = new Result(false, 0, 0);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduled() {
        Result result = scan();
        if (result.breached() > 0) {
            log.info("SLA scan: {} clocks breached, {} tickets escalated", result.breached(), result.escalated());
        }
    }

    public Result scan() {
        if (!lease.tryAcquire(JOB, owner, LEASE)) {
            log.info("SLA scan skipped: another instance holds the lease");
            return Result.SKIPPED;
        }
        try {
            return duration.record(this::scanAll);
        } finally {
            lease.release(JOB, owner);
        }
    }

    private Result scanAll() {
        int breached = 0;
        int escalated = 0;
        while (true) {
            int[] batch = transactions.execute(status -> batch());
            breached += batch[0];
            escalated += batch[1];
            if (batch[0] < BATCH) {
                return new Result(true, breached, escalated);
            }
        }
    }

    private int[] batch() {
        Instant now = clock.instant();
        List<SlaTimer> due = timers.lockOverdue(now, BATCH);
        int escalated = 0;
        for (SlaTimer timer : due) {
            timer.breach(now);
            audit.record(AuditEvent.of("sla.breached")
                    .ticket(timer.ticketId())
                    .change(timer.kind().name().toLowerCase(Locale.ROOT), timer.dueAt(), now));
            @Nullable Priority raisedTo = null;
            if (timer.kind() == Kind.RESOLUTION && ESCALATE.contains(policyPriority(timer))) {
                raisedTo = tickets.escalate(timer.ticketId()).orElse(null);
                escalated += raisedTo != null ? 1 : 0;
            }
            events.publishEvent(new SlaBreached(timer.ticketId(), timer.kind(), timer.dueAt(), now, raisedTo));
        }
        return new int[] {due.size(), escalated};
    }

    private Priority policyPriority(SlaTimer timer) {
        return json.readValue(timer.policySnapshot(), PolicySnapshot.class).priority();
    }
}
