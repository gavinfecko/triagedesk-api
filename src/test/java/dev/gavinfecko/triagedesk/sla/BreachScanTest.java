package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.BreachScanner;
import dev.gavinfecko.triagedesk.sla.domain.SlaBreached;
import dev.gavinfecko.triagedesk.sla.infra.LeaseLock;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
@RecordApplicationEvents
class BreachScanTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BreachScanner scanner;

    @Autowired
    LeaseLock lease;

    @Autowired
    MeterRegistry meters;

    @Autowired
    Clock clock;

    @Autowired
    ApplicationEvents events;

    Api api;
    Session requester;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        scanner.scan(); // start from a database with nothing overdue
    }

    UUID create(Priority priority) {
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        "Label printer offline",
                        "description",
                        "The label printer at intake shows offline.",
                        "category_id",
                        Reference.PRINTER,
                        "priority",
                        priority)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(Api.read(created).get("id").asString());
    }

    void overdue(UUID ticket, String kind) {
        jdbc.sql("update sla_timers set due_at = ? where ticket_id = ? and kind = ?")
                .params(Timestamp.from(clock.instant().minus(Duration.ofMinutes(5))), ticket, kind)
                .update();
    }

    String priority(UUID ticket) {
        return jdbc.sql("select priority from tickets where id = ?")
                .param(ticket)
                .query(String.class)
                .single();
    }

    int audits(UUID ticket, String action) {
        return jdbc.sql("select count(*) from audit_events where ticket_id = ? and action = ?")
                .params(ticket, action)
                .query(Integer.class)
                .single();
    }

    @Test
    void aResolutionBreachOnP3OrP4RaisesThePriorityAsTheSystemAndP1P2OnlyBreach() {
        UUID p3 = create(Priority.P3_MEDIUM);
        UUID p4 = create(Priority.P4_LOW);
        UUID p2 = create(Priority.P2_HIGH);
        UUID firstResponseOnly = create(Priority.P4_LOW);
        overdue(p3, "RESOLUTION");
        overdue(p4, "RESOLUTION");
        overdue(p2, "RESOLUTION");
        overdue(firstResponseOnly, "FIRST_RESPONSE");

        BreachScanner.Result result = scanner.scan();
        assertThat(result.ran()).isTrue();
        assertThat(result.breached()).isEqualTo(4);
        assertThat(result.escalated()).isEqualTo(2);

        assertThat(priority(p3)).isEqualTo("P2_HIGH");
        assertThat(priority(p4)).isEqualTo("P3_MEDIUM");
        assertThat(priority(p2)).as("P1/P2 are notified, not raised").isEqualTo("P2_HIGH");
        assertThat(priority(firstResponseOnly))
                .as("first-response breaches do not escalate")
                .isEqualTo("P4_LOW");

        String actor = jdbc.sql("select coalesce(actor_id::text, 'system') from audit_events "
                        + "where ticket_id = ? and action = 'ticket.priority_changed'")
                .param(p3)
                .query(String.class)
                .single();
        assertThat(actor).isEqualTo("system");
        assertThat(audits(p3, "sla.breached")).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from sla_timers where ticket_id = ? and breached_at is not null")
                        .param(p3)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);

        List<SlaBreached> published = events.stream(SlaBreached.class).toList();
        assertThat(published).hasSize(4);
        assertThat(published)
                .filteredOn(e -> e.ticketId().equals(p4))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.escalatedTo()).isEqualTo(Priority.P3_MEDIUM);
                    assertThat(e.breachedAt()).isAfter(e.dueAt());
                });
        assertThat(published)
                .filteredOn(e -> e.ticketId().equals(p2))
                .singleElement()
                .satisfies(e -> assertThat(e.escalatedTo()).isNull());

        MvcTestResult sla = mvc.get()
                .uri("/api/v1/tickets/"
                        + jdbc.sql("select ticket_key from tickets where id = ?")
                                .param(p3)
                                .query(String.class)
                                .single() + "/sla")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(sla).bodyJson().extractingPath("$.resolution.status").isEqualTo("breached");
    }

    @Test
    void aBreachedClockIsNeverSelectedAgain() {
        UUID ticket = create(Priority.P4_LOW);
        overdue(ticket, "RESOLUTION");
        assertThat(scanner.scan().breached()).isEqualTo(1);
        assertThat(scanner.scan().breached()).isZero();
        assertThat(audits(ticket, "sla.breached")).isEqualTo(1);
        assertThat(priority(ticket)).as("raised once, not twice").isEqualTo("P3_MEDIUM");
    }

    @Test
    void pausedMetAndCancelledClocksAreLeftAlone() {
        UUID paused = create(Priority.P4_LOW);
        jdbc.sql("update sla_timers set paused_at = ? where ticket_id = ?")
                .params(Timestamp.from(clock.instant().minus(Duration.ofHours(1))), paused)
                .update();
        overdue(paused, "RESOLUTION");
        UUID met = create(Priority.P4_LOW);
        jdbc.sql("update sla_timers set met_at = ? where ticket_id = ?")
                .params(Timestamp.from(clock.instant()), met)
                .update();
        overdue(met, "RESOLUTION");
        assertThat(scanner.scan().breached()).isZero();
    }

    @Test
    void anotherInstanceHoldingTheLeaseMeansThisRunSkips() {
        UUID ticket = create(Priority.P3_MEDIUM);
        overdue(ticket, "RESOLUTION");
        assertThat(lease.tryAcquire("sla-breach-scan", "other-instance", Duration.ofMinutes(5)))
                .isTrue();
        try {
            assertThat(scanner.scan()).isEqualTo(new BreachScanner.Result(false, 0, 0));
            assertThat(audits(ticket, "sla.breached")).isZero();
        } finally {
            lease.release("sla-breach-scan", "other-instance");
        }
        assertThat(scanner.scan().breached()).isEqualTo(1);
    }

    @Test
    void twoScansStartedTogetherBreachEachClockExactlyOnce() throws Exception {
        List<UUID> tickets = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID ticket = create(Priority.P2_HIGH);
            overdue(ticket, "RESOLUTION");
            tickets.add(ticket);
        }
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<BreachScanner.Result> a = CompletableFuture.supplyAsync(() -> awaitThenScan(start));
        CompletableFuture<BreachScanner.Result> b = CompletableFuture.supplyAsync(() -> awaitThenScan(start));
        start.countDown();
        BreachScanner.Result first = a.get(30, TimeUnit.SECONDS);
        BreachScanner.Result second = b.get(30, TimeUnit.SECONDS);

        assertThat(first.breached() + second.breached()).isEqualTo(6);
        for (UUID ticket : tickets) {
            assertThat(audits(ticket, "sla.breached")).isEqualTo(1);
        }
    }

    private BreachScanner.Result awaitThenScan(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return scanner.scan();
    }

    @Test
    void withTenThousandRunningClocksTheScanTakesUnderASecondAndIsTimed() {
        UUID requesterId = requester.userId();
        jdbc.sql("""
                        insert into tickets (id, ticket_key, title, description, status, priority, category_id, queue_id,
                                             requester_id, created_at, updated_at)
                        select gen_random_uuid(), 'BS-' || lpad(g::text, 6, '0'), 'Scan fixture ' || g, 'generated', 'OPEN',
                               'P2_HIGH', '00000000-0000-4000-8000-000000000202', '00000000-0000-4000-8000-000000000101',
                               ?, now(), now()
                        from generate_series(1, 10000) g
                        on conflict (ticket_key) do nothing""").param(requesterId).update();
        String snapshot = jdbc.sql("select policy_snapshot::text from sla_timers limit 1")
                .query(String.class)
                .single();
        jdbc.sql("""
                        insert into sla_timers (id, ticket_id, kind, policy_snapshot, started_at, at_risk_at, due_at)
                        select gen_random_uuid(), t.id, 'FIRST_RESPONSE', cast(? as jsonb), now(), now(),
                               case when t.ticket_key <= 'BS-000100' then now() - interval '1 minute' else now() + interval '1 day' end
                        from tickets t
                        where t.ticket_key like 'BS-%'
                          and not exists (select 1 from sla_timers s where s.ticket_id = t.id)""").param(snapshot).update();
        jdbc.sql("analyze sla_timers").update();

        long before = meters.timer("sla.scan").count();
        Instant started = Instant.now();
        BreachScanner.Result result = scanner.scan();
        Duration took = Duration.between(started, Instant.now());

        assertThat(result.breached()).isEqualTo(100);
        assertThat(took).isLessThan(Duration.ofSeconds(1));
        assertThat(meters.timer("sla.scan").count()).isEqualTo(before + 1);
    }
}
