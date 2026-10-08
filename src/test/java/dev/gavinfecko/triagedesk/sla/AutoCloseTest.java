package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.AutoCloser;
import dev.gavinfecko.triagedesk.sla.infra.LeaseLock;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
class AutoCloseTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AutoCloser closer;

    @Autowired
    LeaseLock lease;

    @Autowired
    Clock clock;

    @Autowired
    ApplicationEvents events;

    Api api;
    Session requester;
    Session agent;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
    }

    MvcTestResult post(Session who, String path, Map<String, Object> body) {
        return mvc.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    /** A ticket taken to RESOLVED through the API, then backdated as if resolved {@code ago}. */
    UUID resolved(String priority, Duration ago) {
        MvcTestResult created = post(
                requester,
                "/api/v1/tickets",
                Map.of(
                        "title",
                        "Monitor flickers at desk 4",
                        "description",
                        "Flickers every few seconds since Monday.",
                        "category_id",
                        Reference.PRINTER,
                        "priority",
                        priority));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String key = Api.read(created).get("key").asString();
        transition(agent, key, "OPEN", null);
        transition(agent, key, "RESOLVED", "Replaced the cable.");
        UUID id = UUID.fromString(Api.read(created).get("id").asString());
        backdate(id, ago);
        return id;
    }

    void backdate(UUID id, Duration ago) {
        jdbc.sql("update tickets set resolved_at = ? where id = ?")
                .params(Timestamp.from(clock.instant().minus(ago)), id)
                .update();
    }

    void transition(Session who, String key, String to, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        if (comment != null) {
            body.put("comment", comment);
        }
        assertThat(post(who, "/api/v1/tickets/" + key + "/transitions", body)).hasStatusOk();
    }

    String status(UUID id) {
        return jdbc.sql("select status from tickets where id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    @Test
    void aTicketResolvedMoreThanThreeBusinessDaysAgoIsClosedByTheSystem() {
        UUID stale = resolved("P3_MEDIUM", Duration.ofDays(10));
        UUID fresh = resolved("P3_MEDIUM", Duration.ofHours(2));

        AutoCloser.Result result = closer.run();
        assertThat(result.ran()).isTrue();
        assertThat(result.closed()).isGreaterThanOrEqualTo(1);
        assertThat(status(stale)).isEqualTo("CLOSED");
        assertThat(status(fresh)).isEqualTo("RESOLVED");

        String actor = jdbc.sql(
                        "select coalesce(actor_id::text, 'system') from audit_events "
                                + "where ticket_id = ? and action = 'ticket.status_changed' and after_value = '\"CLOSED\"'::jsonb")
                .param(stale)
                .query(String.class)
                .single();
        assertThat(actor).isEqualTo("system");
        assertThat(jdbc.sql("select closed_at is not null from tickets where id = ?")
                        .param(stale)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(events.stream(TicketStatusChanged.class)
                        .filter(e -> e.ticketId().equals(stale) && e.to() == TicketStatus.CLOSED))
                .singleElement()
                .satisfies(e -> assertThat(e.actorId()).isNull());
    }

    @Test
    void businessDaysAreCountedOnTheTicketsCalendar() {
        // P1 runs on 24x7: 3 days and 1 hour ago is past three business days.
        UUID roundTheClock = resolved("P1_CRITICAL", Duration.ofDays(3).plusHours(1));
        closer.run();
        assertThat(status(roundTheClock)).isEqualTo("CLOSED");

        // P3 runs on clinic hours: whether 3 days and 1 hour is three open days depends on today's weekday.
        UUID clinic = resolved("P3_MEDIUM", Duration.ofDays(3).plusHours(1));
        Instant resolvedAt = jdbc.sql("select resolved_at from tickets where id = ?")
                .param(clinic)
                .query(Timestamp.class)
                .single()
                .toInstant();
        boolean due = !calendarClinic().plusBusinessDays(resolvedAt, 3).isAfter(clock.instant());
        closer.run();
        assertThat(status(clinic)).isEqualTo(due ? "CLOSED" : "RESOLVED");
    }

    @Test
    void aReopenedTicketIsNeverTouched() {
        UUID ticket = resolved("P3_MEDIUM", Duration.ofHours(1));
        String key = jdbc.sql("select ticket_key from tickets where id = ?")
                .param(ticket)
                .query(String.class)
                .single();
        transition(requester, key, "OPEN", "Still flickering.");
        backdate(ticket, Duration.ofDays(10)); // resolved_at was cleared by the reopen; force an old value anyway
        closer.run();
        assertThat(status(ticket)).isEqualTo("OPEN");
    }

    @Test
    void anotherInstanceHoldingTheLeaseMeansThisRunSkips() {
        UUID stale = resolved("P2_HIGH", Duration.ofDays(10));
        assertThat(lease.tryAcquire("auto-close", "other-instance", Duration.ofMinutes(5)))
                .isTrue();
        try {
            assertThat(closer.run()).isEqualTo(new AutoCloser.Result(false, 0));
            assertThat(status(stale)).isEqualTo("RESOLVED");
        } finally {
            lease.release("auto-close", "other-instance");
        }
        closer.run();
        assertThat(status(stale)).isEqualTo("CLOSED");
    }

    @Autowired
    dev.gavinfecko.triagedesk.sla.application.CalendarService calendars;

    dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar calendarClinic() {
        return calendars.calendar(Reference.CLINIC_CALENDAR);
    }
}
