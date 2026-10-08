package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
class SlaTicketListTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Clock clock;

    Api api;
    Session requester;
    Session agent;
    String onTrack;
    String atRisk;
    String breached;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        onTrack = create("Scanner at intake is slow");
        atRisk = create("Badge reader at the side door");
        breached = create("Fax line has no dial tone");
        Instant now = clock.instant();
        clock(atRisk, now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(30)));
        clock(breached, now.minus(Duration.ofMinutes(60)), now.minus(Duration.ofMinutes(5)));
    }

    String create(String title) {
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        title,
                        "description",
                        "Reported at the front desk this morning.",
                        "category_id",
                        Reference.PRINTER,
                        "priority",
                        "P1_CRITICAL")))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return Api.read(created).get("key").asString();
    }

    void clock(String key, Instant atRiskAt, Instant dueAt) {
        jdbc.sql("""
                        update sla_timers set at_risk_at = ?, due_at = ?
                        where kind = 'RESOLUTION' and ticket_id = (select id from tickets where ticket_key = ?)""")
                .params(Timestamp.from(atRiskAt), Timestamp.from(dueAt), key)
                .update();
    }

    MvcTestResult list(Session who, String query) {
        return mvc.get()
                .uri("/api/v1/tickets?requester_id=" + requester.userId() + "&" + query)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    @Test
    void everySummaryCarriesItsResolutionAndFirstResponseState() {
        MvcTestResult result = list(agent, "sort=key,asc");
        assertThat(result).hasStatusOk();
        JsonNode items = Api.read(result).get("items");
        assertThat(items.size()).isEqualTo(3);
        Map<String, String> statusByKey = new java.util.HashMap<>();
        items.valueStream()
                .forEach(i -> statusByKey.put(
                        i.get("key").asString(),
                        i.get("sla").get("resolution_status").asString()));
        assertThat(statusByKey)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(onTrack, "on_track", atRisk, "at_risk", breached, "breached"));
        items.valueStream().forEach(i -> {
            assertThat(i.get("sla").get("first_response_status").asString()).isEqualTo("on_track");
            assertThat(i.get("sla").get("resolution_due_at").isNull()).isFalse();
        });
    }

    @Test
    void slaStatusFiltersOnTheResolutionClock() {
        MvcTestResult result = list(agent, "sla_status=at_risk,breached");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactlyInAnyOrder(atRisk, breached);
        assertThat(result).bodyJson().extractingPath("$.page.total_elements").isEqualTo(2);
        assertThat(list(agent, "sla_status=on_track"))
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly(onTrack);
    }

    @Test
    void slaDueSortsSoonestDueFirst() {
        assertThat(list(agent, "sort=sla_due,asc"))
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly(breached, atRisk, onTrack);
        assertThat(list(agent, "sort=sla_due,desc"))
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly(onTrack, atRisk, breached);
    }

    @Test
    void requestersSeeTheSameFieldsOnTheirOwnTickets() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/tickets?sla_status=breached")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].key").asArray().containsExactly(breached);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].sla.resolution_status")
                .isEqualTo("breached");
    }

    @Test
    void unknownStatusesAndMixedSlaSortsAreRefused() {
        assertThat(list(agent, "sla_status=late"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("sla_status");
        assertThat(list(agent, "sort=sla_due,asc&sort=priority,desc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("sort");
        assertThat(list(agent, "sort=sla_due,sideways")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
