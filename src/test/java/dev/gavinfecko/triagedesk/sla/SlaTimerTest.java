package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.CalendarService;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

@ApiTest
class SlaTimerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CalendarService calendars;

    @Autowired
    Clock clock;

    Api api;
    Session requester;
    Session agent;
    Session admin;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        admin = api.loginAs(Role.ADMIN);
    }

    MvcTestResult post(Session who, String path, Map<String, Object> body) {
        return mvc.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    MvcTestResult get(Session who, String path) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    String create(Priority priority) {
        MvcTestResult created = post(
                requester,
                "/api/v1/tickets",
                Map.of(
                        "title",
                        "Printer on 2 jams every page",
                        "description",
                        "Paper jams on every print since this morning.",
                        "category_id",
                        Reference.PRINTER,
                        "priority",
                        priority));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return Api.read(created).get("key").asString();
    }

    JsonNode sla(Session who, String key) {
        MvcTestResult result = get(who, "/api/v1/tickets/" + key + "/sla");
        assertThat(result).hasStatusOk();
        return Api.read(result);
    }

    void transition(Session who, String key, String to, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        if (comment != null) {
            body.put("comment", comment);
        }
        assertThat(post(who, "/api/v1/tickets/" + key + "/transitions", body)).hasStatusOk();
    }

    void reply(Session who, String key, String text) {
        assertThat(post(who, "/api/v1/tickets/" + key + "/comments", Map.of("body", text, "visibility", "PUBLIC")))
                .hasStatus(HttpStatus.CREATED);
    }

    UUID ticketId(String key) {
        return jdbc.sql("select id from tickets where ticket_key = ?")
                .param(key)
                .query(UUID.class)
                .single();
    }

    static Instant at(JsonNode node, String field) {
        return Instant.parse(node.get(field).asString());
    }

    @Test
    void aNewTicketGetsBothClocksFromItsPolicyOnTheClinicCalendar() {
        String key = create(Priority.P2_HIGH);
        Instant createdAt = at(Api.read(get(requester, "/api/v1/tickets/" + key)), "created_at");
        JsonNode view = sla(requester, key);
        assertThat(view.get("ticket_key").asString()).isEqualTo(key);

        JsonNode first = view.get("first_response");
        assertThat(first.get("status").asString()).isEqualTo("on_track");
        assertThat(first.get("policy_priority").asString()).isEqualTo("P2_HIGH");
        assertThat(first.get("calendar_id").asString()).isEqualTo(Reference.CLINIC_CALENDAR.toString());
        assertThat(first.get("budget_minutes").asInt()).isEqualTo(60);
        assertThat(at(first, "started_at")).isEqualTo(createdAt);
        Instant expectedDue = calendars.calendar(Reference.CLINIC_CALENDAR).add(createdAt, Duration.ofMinutes(60));
        assertThat(at(first, "due_at")).isCloseTo(expectedDue, within(1, ChronoUnit.SECONDS));
        assertThat(first.get("remaining_business_minutes").asLong()).isBetween(59L, 60L);
        assertThat(first.get("paused_total_minutes").asLong()).isZero();

        JsonNode resolution = view.get("resolution");
        assertThat(resolution.get("budget_minutes").asInt()).isEqualTo(480);
        assertThat(resolution.get("status").asString()).isEqualTo("on_track");
        assertThat(resolution.get("remaining_business_minutes").asLong()).isBetween(479L, 480L);

        Session someoneElse = api.loginAs(Role.REQUESTER);
        assertThat(get(someoneElse, "/api/v1/tickets/" + key + "/sla")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get(agent, "/api/v1/tickets/" + key + "/sla")).hasStatusOk();
    }

    @Test
    void theFirstPublicStaffReplyMeetsTheFirstResponseClock() {
        String key = create(Priority.P3_MEDIUM);
        reply(agent, key, "Looking at it now.");
        JsonNode view = sla(agent, key);
        assertThat(view.get("first_response").get("status").asString()).isEqualTo("met");
        assertThat(view.get("first_response").get("met_at").isNull()).isFalse();
        assertThat(view.get("first_response").get("remaining_business_minutes").asLong())
                .isZero();
        assertThat(view.get("resolution").get("status").asString()).isEqualTo("on_track");
    }

    @Test
    void pendingPausesTheResolutionClockAndResumingPushesTheDueInstantOutByThePause() {
        String key = create(Priority.P1_CRITICAL); // 24x7: the arithmetic is exact
        transition(agent, key, "OPEN", null);
        Instant dueBefore = at(sla(agent, key).get("resolution"), "due_at");
        transition(agent, key, "PENDING", "Waiting on you: which printer is it?");
        JsonNode paused = sla(agent, key).get("resolution");
        assertThat(paused.get("status").asString()).isEqualTo("paused");
        assertThat(paused.get("paused_at").isNull()).isFalse();
        assertThat(paused.get("remaining_business_minutes").asLong()).isBetween(238L, 240L);

        jdbc.sql(
                        "update sla_timers set paused_at = paused_at - interval '2 hours' where ticket_id = ? and kind = 'RESOLUTION'")
                .param(ticketId(key))
                .update();
        reply(requester, key, "The one by the front desk.");
        JsonNode resumed = sla(agent, key).get("resolution");
        assertThat(resumed.get("status").asString()).isEqualTo("on_track");
        assertThat(resumed.get("paused_at").isNull()).isTrue();
        assertThat(resumed.get("paused_total_minutes").asLong()).isEqualTo(120);
        assertThat(at(resumed, "due_at"))
                .isBetween(dueBefore.plus(Duration.ofHours(2)), dueBefore.plus(Duration.ofMinutes(121)));
    }

    @Test
    void aPriorityChangeReschedulesTheOpenClocksFromTheirOriginalStartAndIsAudited() {
        String key = create(Priority.P4_LOW);
        String etag = get(admin, "/api/v1/tickets/" + key).getResponse().getHeader(HttpHeaders.ETAG);
        MvcTestResult patched = mvc.patch()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("priority", "P1_CRITICAL")))
                .exchange();
        assertThat(patched).hasStatusOk();

        JsonNode view = sla(admin, key);
        JsonNode resolution = view.get("resolution");
        assertThat(resolution.get("policy_priority").asString()).isEqualTo("P1_CRITICAL");
        assertThat(resolution.get("calendar_id").asString()).isEqualTo(Reference.ALWAYS_OPEN_CALENDAR.toString());
        assertThat(resolution.get("budget_minutes").asInt()).isEqualTo(240);
        assertThat(at(resolution, "due_at"))
                .isEqualTo(at(resolution, "started_at").plus(Duration.ofMinutes(240)));
        JsonNode first = view.get("first_response");
        assertThat(first.get("budget_minutes").asInt()).isEqualTo(15);
        assertThat(at(first, "due_at")).isEqualTo(at(first, "started_at").plus(Duration.ofMinutes(15)));

        Integer audits = jdbc.sql(
                        "select count(*) from audit_events where action = 'sla.timer_rescheduled' and ticket_id = ?")
                .param(ticketId(key))
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void resolvingMeetsTheClockReopeningStartsAFreshOneAndCancellingStopsBoth() {
        String key = create(Priority.P1_CRITICAL);
        transition(agent, key, "OPEN", null);
        transition(agent, key, "RESOLVED", "Replaced the fuser; printing again.");
        JsonNode resolved = sla(agent, key).get("resolution");
        assertThat(resolved.get("status").asString()).isEqualTo("met");
        String firstClock = resolved.get("id").asString();

        transition(requester, key, "OPEN", "Jammed again an hour later.");
        JsonNode reopened = sla(agent, key).get("resolution");
        assertThat(reopened.get("id").asString()).isNotEqualTo(firstClock);
        assertThat(reopened.get("status").asString()).isEqualTo("on_track");
        assertThat(at(reopened, "started_at")).isAfterOrEqualTo(at(resolved, "met_at"));
        assertThat(reopened.get("remaining_business_minutes").asLong()).isBetween(239L, 240L);
        Integer resolutionClocks = jdbc.sql(
                        "select count(*) from sla_timers where ticket_id = ? and kind = 'RESOLUTION'")
                .param(ticketId(key))
                .query(Integer.class)
                .single();
        assertThat(resolutionClocks).isEqualTo(2);

        String cancelled = create(Priority.P2_HIGH);
        transition(requester, cancelled, "CANCELLED", null);
        JsonNode stopped = sla(requester, cancelled);
        assertThat(stopped.get("first_response").get("status").asString()).isEqualTo("cancelled");
        assertThat(stopped.get("resolution").get("status").asString()).isEqualTo("cancelled");
        assertThat(stopped.get("resolution").get("remaining_business_minutes").asLong())
                .isZero();
    }

    @Test
    void atRiskAndBreachedComeFromTheBusinessTimeLeft() {
        String key = create(Priority.P1_CRITICAL);
        Instant now = clock.instant();
        jdbc.sql(
                        "update sla_timers set started_at = ?, due_at = ?, at_risk_at = ? where ticket_id = ? and kind = 'RESOLUTION'")
                .params(
                        Timestamp.from(now.minus(Duration.ofMinutes(200))),
                        Timestamp.from(now.plus(Duration.ofMinutes(40))),
                        Timestamp.from(now.minus(Duration.ofMinutes(20))),
                        ticketId(key))
                .update();
        JsonNode atRisk = sla(agent, key).get("resolution");
        assertThat(atRisk.get("status").asString()).isEqualTo("at_risk");
        assertThat(atRisk.get("remaining_business_minutes").asLong()).isBetween(39L, 40L);
        assertThat(sla(agent, key).get("first_response").get("status").asString())
                .isEqualTo("on_track");

        jdbc.sql("update sla_timers set due_at = ? where ticket_id = ? and kind = 'RESOLUTION'")
                .params(Timestamp.from(now.minus(Duration.ofMinutes(1))), ticketId(key))
                .update();
        JsonNode breached = sla(agent, key).get("resolution");
        assertThat(breached.get("status").asString()).isEqualTo("breached");
        assertThat(breached.get("remaining_business_minutes").asLong()).isZero();

        transition(agent, key, "OPEN", null);
        transition(agent, key, "RESOLVED", "Late, but done.");
        assertThat(sla(agent, key).get("resolution").get("status").asString())
                .as("met after the due instant still reads as breached")
                .isEqualTo("breached");
    }
}
