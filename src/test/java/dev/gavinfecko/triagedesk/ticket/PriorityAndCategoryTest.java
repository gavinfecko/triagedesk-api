package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketPriorityChanged;
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
class PriorityAndCategoryTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    Api api;
    Session requester;
    Session agent;
    String key;

    @BeforeEach
    void openATicket() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        "Suspicious email asking for my password",
                        "description",
                        "Looks like Microsoft.",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P4_LOW")))
                .exchange();
        key = Api.read(created).get("key").asString();
    }

    String etag(Session who) {
        return mvc.get()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }

    MvcTestResult patch(Session who, Map<String, Object> body) {
        return mvc.patch()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .header(HttpHeaders.IF_MATCH, etag(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    Map<String, Object> row() {
        return jdbc.sql("select priority, category_id, queue_id, title from tickets where ticket_key = ?")
                .param(key)
                .query()
                .singleRow();
    }

    int audits(String action) {
        return jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = ?")
                .params(key, action)
                .query(Integer.class)
                .single();
    }

    @Test
    void anAgentRaisesThePriorityWithAuditEventAndNewEtag() {
        MvcTestResult raised = patch(agent, Map.of("priority", "P2_HIGH"));
        assertThat(raised).hasStatusOk();
        assertThat(raised).bodyJson().extractingPath("$.priority").isEqualTo("P2_HIGH");
        assertThat(raised.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"1\"");
        assertThat(audits("ticket.priority_changed")).isEqualTo(1);
        String before = jdbc.sql(
                        "select before_value::text from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.priority_changed'")
                .param(key)
                .query(String.class)
                .single();
        assertThat(before).isEqualTo("\"P4_LOW\"");
        assertThat(events.stream(TicketPriorityChanged.class)
                        .filter(e -> e.key().equals(key)))
                .singleElement()
                .satisfies(e -> assertThat(e.to().name()).isEqualTo("P2_HIGH"));

        assertThat(patch(agent, Map.of("priority", "P2_HIGH"))).hasStatusOk(); // same value: nothing happens
        assertThat(audits("ticket.priority_changed")).isEqualTo(1);
        assertThat(etag(agent)).isEqualTo("\"1\"");
    }

    @Test
    void recategorisingCorrectsTheCategoryButLeavesTheQueueAlone() {
        assertThat(row().get("queue_id")).isEqualTo(Reference.HARDWARE_QUEUE);
        MvcTestResult fixed =
                patch(agent, Map.of("category_id", Reference.SECURITY_INCIDENT.toString(), "priority", "P1_CRITICAL"));
        assertThat(fixed).hasStatusOk();
        assertThat(fixed).bodyJson().extractingPath("$.category.name").isEqualTo("Security incident");
        assertThat(fixed).bodyJson().extractingPath("$.queue.name").isEqualTo("Hardware");
        assertThat(audits("ticket.category_changed")).isEqualTo(1);
        assertThat(audits("ticket.priority_changed")).isEqualTo(1);
        assertThat(etag(agent)).isEqualTo("\"1\""); // one write, one version bump
    }

    @Test
    void unknownCategoryIsAValidationProblemAndChangesNothing() {
        MvcTestResult bad =
                patch(agent, Map.of("category_id", UUID.randomUUID().toString(), "priority", "P1_CRITICAL"));
        assertThat(bad)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("category_id");
        assertThat(row().get("priority")).isEqualTo("P4_LOW");
        assertThat(etag(agent)).isEqualTo("\"0\"");
    }

    @Test
    void requestersMayRewordButNeverTouchPriorityOrCategory() {
        assertThat(patch(requester, Map.of("title", "Phishing email asking for my password")))
                .hasStatusOk();
        MvcTestResult priority = patch(requester, Map.of("priority", "P1_CRITICAL"));
        assertThat(priority).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult mixed = patch(
                requester, Map.of("title", "A completely different title", "category_id", Reference.EHR.toString()));
        assertThat(mixed).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(row().get("title"))
                .isEqualTo("Phishing email asking for my password"); // the mixed edit applied nothing
        assertThat(row().get("priority")).isEqualTo("P4_LOW");
    }
}
