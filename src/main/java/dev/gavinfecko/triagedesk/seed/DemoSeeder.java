package dev.gavinfecko.triagedesk.seed;

import dev.gavinfecko.triagedesk.seed.DemoData.Person;
import dev.gavinfecko.triagedesk.seed.DemoData.Problem;
import dev.gavinfecko.triagedesk.sla.application.CalendarService;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyService;
import dev.gavinfecko.triagedesk.sla.application.SlaTimerService;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
import dev.gavinfecko.triagedesk.ticket.domain.TicketFirstResponded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo clinic (TD-15): an admin, two agents, six requesters and 60 tickets across every
 * status, priority, queue and age, so lists and dashboards mean something on first run. Idempotent:
 * every row has a stable id and is only inserted if missing. On in the {@code dev} and {@code seed}
 * profiles ({@code triagedesk.seed.enabled}); never in production.
 */
@Component
@ConditionalOnProperty("triagedesk.seed.enabled")
public class DemoSeeder implements ApplicationRunner {

    static final int TICKETS = 60;
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    // Status mix over 60 tickets: 12 NEW, 15 OPEN, 8 PENDING, 12 RESOLVED, 10 CLOSED, 3 CANCELLED.
    private static final String[] STATUS_PLAN = plan();

    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SlaTimerService timers;
    private final SlaPolicyService policies;
    private final CalendarService calendars;

    public DemoSeeder(
            JdbcClient jdbc,
            PasswordEncoder passwords,
            TransactionTemplate transactions,
            Clock clock,
            SlaTimerService timers,
            SlaPolicyService policies,
            CalendarService calendars) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.transactions = transactions;
        this.clock = clock;
        this.timers = timers;
        this.policies = policies;
        this.calendars = calendars;
    }

    @Override
    public void run(ApplicationArguments args) {
        Result result = seed();
        log.info(
                "Demo data: {} users and {} tickets added (password for every demo user: {})",
                result.users(),
                result.tickets(),
                DemoData.PASSWORD);
    }

    public record Result(int users, int tickets) {}

    public Result seed() {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            String hash = passwords.encode(DemoData.PASSWORD);
            int users = 0;
            for (Person person : everyone()) {
                users += insertUser(person, hash, now.minus(Duration.ofDays(60)));
            }
            int tickets = 0;
            for (int i = 0; i < TICKETS; i++) {
                tickets += insertTicket(i, now);
            }
            return new Result(users, tickets);
        });
    }

    static List<Person> everyone() {
        List<Person> all = new ArrayList<>();
        all.add(DemoData.ADMIN);
        all.addAll(DemoData.AGENTS);
        all.addAll(DemoData.REQUESTERS);
        return all;
    }

    private int insertUser(Person person, String hash, Instant created) {
        return jdbc.sql("""
                        insert into users (id, email, display_name, password_hash, role, active, created_at, updated_at)
                        values (?, ?, ?, ?, ?, true, ?, ?)
                        on conflict do nothing""")
                .params(
                        person.id(),
                        person.email(),
                        person.displayName(),
                        hash,
                        person.role(),
                        Timestamp.from(created),
                        Timestamp.from(created))
                .update();
    }

    private int insertTicket(int i, Instant now) {
        UUID id = SeedIds.of("ticket:" + i);
        Problem problem = DemoData.PROBLEMS.get(i % DemoData.PROBLEMS.size());
        Person requester = DemoData.REQUESTERS.get(i % DemoData.REQUESTERS.size());
        String status = STATUS_PLAN[i];
        // Spread over the last ~3 weeks; lower i is older, and older tickets are the finished ones.
        Instant created = now.minus(Duration.ofHours(8L * (TICKETS - i) + (i % 5)));
        if (status.equals("OPEN") && i % 5 == 0) {
            // Three open tickets sit inside the at-risk window, so the SLA views have something to show on day one.
            created = atRiskStart(Priority.valueOf(problem.priority()), now);
        }
        boolean worked = !status.equals("NEW") && !status.equals("CANCELLED");
        @Nullable UUID assignee = worked ? DemoData.AGENTS.get(i % 2).id() : null;
        @Nullable Instant firstResponse = worked ? created.plus(Duration.ofMinutes(20 + (i % 7) * 10L)) : null;
        boolean finished = status.equals("RESOLVED") || status.equals("CLOSED");
        @Nullable Instant resolved = finished ? created.plus(Duration.ofHours(2 + (i % 6) * 6L)) : null;
        @Nullable Instant closed = status.equals("CLOSED") ? resolved.plus(Duration.ofDays(1)) : null;
        Instant updated =
                closed != null ? closed : resolved != null ? resolved : firstResponse != null ? firstResponse : created;

        int inserted = jdbc.sql("""
                        insert into tickets (id, ticket_key, title, description, status, priority, category_id, queue_id,
                                             requester_id, assignee_id, created_at, updated_at,
                                             first_responded_at, resolved_at, closed_at)
                        select ?, 'HD-' || lpad(nextval('ticket_key_seq')::text, 6, '0'), ?, ?, ?, ?, c.id, c.default_queue_id,
                               ?, ?, ?, ?, ?, ?, ?
                        from categories c
                        where c.id = ? and not exists (select 1 from tickets where id = ?)""")
                .params(
                        id,
                        problem.title(),
                        problem.description(),
                        status,
                        problem.priority(),
                        requester.id(),
                        assignee,
                        Timestamp.from(created),
                        Timestamp.from(updated),
                        ts(firstResponse),
                        ts(resolved),
                        ts(closed),
                        problem.categoryId(),
                        id)
                .update();
        if (inserted == 1) {
            history(id, status, requester.id(), assignee, created, firstResponse, resolved, closed);
            clocks(
                    id,
                    status,
                    Priority.valueOf(problem.priority()),
                    requester.id(),
                    assignee,
                    created,
                    firstResponse,
                    resolved);
        }
        return inserted;
    }

    /** The clocks a ticket in this status would have, driven through the same listeners as real tickets. */
    private void clocks(
            UUID id,
            String status,
            Priority priority,
            UUID requester,
            @Nullable UUID assignee,
            Instant created,
            @Nullable Instant firstResponse,
            @Nullable Instant resolved) {
        String key = jdbc.sql("select ticket_key from tickets where id = ?")
                .param(id)
                .query(String.class)
                .single();
        timers.onTicketCreated(new TicketCreated(id, key, priority, requester, created));
        if (status.equals("CANCELLED")) {
            timers.onStatusChanged(new TicketStatusChanged(
                    id, key, TicketStatus.NEW, TicketStatus.CANCELLED, requester, created.plus(Duration.ofHours(1))));
            return;
        }
        if (firstResponse == null || assignee == null) {
            return;
        }
        timers.onFirstResponse(new TicketFirstResponded(id, key, assignee, firstResponse));
        if (status.equals("PENDING")) {
            timers.onStatusChanged(new TicketStatusChanged(
                    id,
                    key,
                    TicketStatus.OPEN,
                    TicketStatus.PENDING,
                    assignee,
                    firstResponse.plus(Duration.ofMinutes(30))));
        }
        if (resolved != null) {
            timers.onStatusChanged(
                    new TicketStatusChanged(id, key, TicketStatus.OPEN, TicketStatus.RESOLVED, assignee, resolved));
        }
    }

    /** A creation instant from which about 80 % of the priority's resolution budget has already run. */
    private Instant atRiskStart(Priority priority, Instant now) {
        return policies.activeFor(priority)
                .map(policy -> {
                    BusinessCalendar calendar = calendars.calendar(policy.calendarId());
                    Duration target = Duration.ofMinutes(policy.resolutionMinutes())
                            .multipliedBy(4)
                            .dividedBy(5);
                    Instant start = now;
                    while (calendar.elapsed(start, now).compareTo(target) < 0) {
                        start = start.minus(Duration.ofMinutes(10));
                    }
                    return start;
                })
                .orElse(now.minus(Duration.ofHours(2)));
    }

    /** The audit rows a ticket in this status would have accumulated, in order (TD-31). */
    private void history(
            UUID ticket,
            String status,
            UUID requester,
            @Nullable UUID assignee,
            Instant created,
            @Nullable Instant firstResponse,
            @Nullable Instant resolved,
            @Nullable Instant closed) {
        auditRow(ticket, "ticket.created", requester, "status", null, "NEW", created);
        if (status.equals("CANCELLED")) {
            auditRow(
                    ticket,
                    "ticket.status_changed",
                    requester,
                    "status",
                    "NEW",
                    "CANCELLED",
                    created.plus(Duration.ofMinutes(5)));
            return;
        }
        if (assignee == null || firstResponse == null) {
            return;
        }
        auditRow(ticket, "ticket.assigned", assignee, "assignee_id", null, assignee.toString(), firstResponse);
        auditRow(ticket, "ticket.status_changed", assignee, "status", "NEW", "OPEN", firstResponse);
        if (status.equals("PENDING")) {
            auditRow(
                    ticket,
                    "ticket.status_changed",
                    assignee,
                    "status",
                    "OPEN",
                    "PENDING",
                    firstResponse.plus(Duration.ofMinutes(30)));
        }
        if (resolved != null) {
            auditRow(ticket, "ticket.status_changed", assignee, "status", "OPEN", "RESOLVED", resolved);
        }
        if (closed != null) {
            auditRow(ticket, "ticket.status_changed", requester, "status", "RESOLVED", "CLOSED", closed);
        }
    }

    private void auditRow(
            UUID ticket, String action, UUID actor, String field, @Nullable String before, String after, Instant at) {
        jdbc.sql("""
                        insert into audit_events (action, actor_id, ticket_id, field, before_value, after_value, created_at)
                        values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)""")
                .params(
                        action,
                        actor,
                        ticket,
                        field,
                        before == null ? null : "\"" + before + "\"",
                        "\"" + after + "\"",
                        Timestamp.from(at))
                .update();
    }

    private static @Nullable Timestamp ts(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String[] plan() {
        String[] plan = new String[TICKETS];
        int i = 0;
        // Oldest first: finished work, then waiting and active work, then the newest unassigned tickets.
        for (int n = 0; n < 10; n++) plan[i++] = "CLOSED";
        for (int n = 0; n < 12; n++) plan[i++] = "RESOLVED";
        for (int n = 0; n < 3; n++) plan[i++] = "CANCELLED";
        for (int n = 0; n < 8; n++) plan[i++] = "PENDING";
        for (int n = 0; n < 15; n++) plan[i++] = "OPEN";
        for (int n = 0; n < 12; n++) plan[i++] = "NEW";
        return plan;
    }
}
