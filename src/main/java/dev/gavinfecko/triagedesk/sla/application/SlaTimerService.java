package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.sla.application.TicketSlaView.TimerView;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.sla.infra.SlaTimerRepository;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.application.TicketView;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
import dev.gavinfecko.triagedesk.ticket.domain.TicketFirstResponded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketPriorityChanged;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the SLA clocks in step with the ticket lifecycle. Listens to the ticket module's domain events inside the
 * publishing transaction, so a ticket change and its clock change commit together or not at all.
 */
@Service
public class SlaTimerService {

    private final SlaTimerRepository timers;
    private final SlaPolicyService policies;
    private final CalendarService calendars;
    private final TicketService tickets;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    public SlaTimerService(
            SlaTimerRepository timers,
            SlaPolicyService policies,
            CalendarService calendars,
            TicketService tickets,
            AuditLog audit,
            JsonMapper json,
            Clock clock) {
        this.timers = timers;
        this.policies = policies;
        this.calendars = calendars;
        this.tickets = tickets;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    /** A new ticket gets both clocks from the active policy for its priority; no policy, no clocks. */
    @EventListener
    @Transactional
    public void onTicketCreated(TicketCreated event) {
        policies.activeFor(event.priority()).ifPresent(policy -> {
            PolicySnapshot snapshot = PolicySnapshot.of(policy);
            BusinessCalendar calendar = calendars.calendar(snapshot.calendarId());
            for (Kind kind : Kind.values()) {
                timers.save(SlaTimer.start(
                        event.ticketId(),
                        kind,
                        json.writeValueAsString(snapshot),
                        event.createdAt(),
                        calendar,
                        snapshot.budgetFor(kind)));
            }
        });
    }

    @EventListener
    @Transactional
    public void onFirstResponse(TicketFirstResponded event) {
        current(event.ticketId(), Kind.FIRST_RESPONSE)
                .filter(SlaTimer::isOpen)
                .ifPresent(timer -> timer.meet(event.at(), calendarOf(timer)));
    }

    /** PENDING pauses the resolution clock, OPEN after PENDING resumes it, RESOLVED meets it, a reopen starts a fresh one. */
    @EventListener
    @Transactional
    public void onStatusChanged(TicketStatusChanged event) {
        switch (event.to()) {
            case PENDING -> openResolution(event.ticketId()).ifPresent(timer -> timer.pause(event.at()));
            case OPEN -> {
                if (event.from() == TicketStatus.PENDING) {
                    openResolution(event.ticketId())
                            .ifPresent(timer -> timer.resume(
                                    event.at(), calendarOf(timer), parse(timer).budgetFor(timer.kind())));
                } else if (event.from() == TicketStatus.RESOLVED) {
                    restartResolution(event.ticketId(), event.at());
                }
            }
            case RESOLVED ->
                openResolution(event.ticketId()).ifPresent(timer -> timer.meet(event.at(), calendarOf(timer)));
            case CANCELLED ->
                timers.findByTicketIdOrderByStartedAtAsc(event.ticketId()).stream()
                        .filter(SlaTimer::isOpen)
                        .forEach(timer -> timer.cancel(event.at()));
            default -> {
                // NEW and CLOSED change nothing on the clocks.
            }
        }
    }

    /** The open clocks are recomputed from their original start with the new priority's policy, pauses kept. */
    @EventListener
    @Transactional
    public void onPriorityChanged(TicketPriorityChanged event) {
        policies.activeFor(event.to()).ifPresent(policy -> {
            PolicySnapshot snapshot = PolicySnapshot.of(policy);
            String snapshotJson = json.writeValueAsString(snapshot);
            BusinessCalendar calendar = calendars.calendar(snapshot.calendarId());
            for (SlaTimer timer : timers.findByTicketIdOrderByStartedAtAsc(event.ticketId())) {
                if (!timer.isOpen()) {
                    continue;
                }
                Instant before = timer.dueAt();
                Duration budget = snapshot.budgetFor(timer.kind());
                Instant due = calendar.add(timer.startedAt(), budget.plusSeconds(timer.pausedTotalSeconds()));
                timer.reschedule(snapshotJson, due, SlaTimer.atRiskPoint(event.at(), due, budget, calendar));
                audit.record(AuditEvent.of("sla.timer_rescheduled")
                        .actor(event.actorId())
                        .ticket(event.ticketId())
                        .change(timer.kind().name().toLowerCase(Locale.ROOT) + "_due_at", before, timer.dueAt()));
            }
        });
    }

    /** The clocks of one ticket, with the same visibility rule as the ticket itself. */
    @Transactional(readOnly = true)
    public TicketSlaView forTicket(String key) {
        TicketView ticket = tickets.get(key);
        List<TimerView> current = timersOf(ticket.id());
        return new TicketSlaView(
                ticket.key(),
                current.stream()
                        .filter(t -> t.kind() == Kind.FIRST_RESPONSE)
                        .findFirst()
                        .orElse(null),
                current.stream()
                        .filter(t -> t.kind() == Kind.RESOLUTION)
                        .findFirst()
                        .orElse(null));
    }

    /** The newest clock of each kind on a ticket, as of now. No visibility check: callers inside the module only. */
    @Transactional(readOnly = true)
    public List<TimerView> timersOf(UUID ticketId) {
        Instant now = clock.instant();
        List<TimerView> views = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            current(ticketId, kind).ifPresent(timer -> views.add(view(timer, now)));
        }
        return views;
    }

    private void restartResolution(UUID ticketId, Instant at) {
        current(ticketId, Kind.RESOLUTION)
                .ifPresent(previous -> policies.activeFor(parse(previous).priority())
                        .ifPresent(policy -> {
                            PolicySnapshot snapshot = PolicySnapshot.of(policy);
                            BusinessCalendar calendar = calendars.calendar(snapshot.calendarId());
                            timers.save(SlaTimer.start(
                                    ticketId,
                                    Kind.RESOLUTION,
                                    json.writeValueAsString(snapshot),
                                    at,
                                    calendar,
                                    snapshot.budgetFor(Kind.RESOLUTION)));
                        }));
    }

    private Optional<SlaTimer> openResolution(UUID ticketId) {
        return current(ticketId, Kind.RESOLUTION).filter(SlaTimer::isOpen);
    }

    private Optional<SlaTimer> current(UUID ticketId, Kind kind) {
        return timers.findByTicketIdOrderByStartedAtAsc(ticketId).stream()
                .filter(t -> t.kind() == kind)
                .max(Comparator.comparing(SlaTimer::startedAt));
    }

    private PolicySnapshot parse(SlaTimer timer) {
        return json.readValue(timer.policySnapshot(), PolicySnapshot.class);
    }

    private BusinessCalendar calendarOf(SlaTimer timer) {
        return calendars.calendar(parse(timer).calendarId());
    }

    private TimerView view(SlaTimer timer, Instant now) {
        PolicySnapshot snapshot = parse(timer);
        BusinessCalendar calendar = calendars.calendar(snapshot.calendarId());
        return new TimerView(
                timer.id(),
                timer.kind(),
                timer.status(now),
                snapshot.priority(),
                snapshot.calendarId(),
                (int) snapshot.budgetFor(timer.kind()).toMinutes(),
                timer.startedAt(),
                timer.dueAt(),
                timer.remaining(calendar, now).toMinutes(),
                timer.pausedTotalSeconds() / 60,
                timer.pausedAt(),
                timer.metAt(),
                timer.breachedAt(),
                timer.cancelledAt());
    }
}
