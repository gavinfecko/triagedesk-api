package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

@ApiTest
class AuditTrailTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    Session requester;
    Session agent;
    String key;

    @BeforeEach
    void workATicket() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        "Computer very slow after last update",
                        "description",
                        "Ten minutes to start.",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P3_MEDIUM")))
                .exchange();
        key = Api.read(created).get("key").asString();
        post(agent, "/assign", Map.of("assignee_id", "me"));
        post(agent, "/queue", Map.of("queue_id", Reference.NETWORK.toString()));
        post(
                agent,
                "/comments",
                Map.of("visibility", "INTERNAL", "body", "Probably the antivirus scan after the update."));
        post(agent, "/transitions", Map.of("to", "PENDING", "comment", "Can you leave it on overnight?"));
        String etag = mvc.get()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
        mvc.patch()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("priority", "P4_LOW", "title", "Workstation slow to start after the update")))
                .exchange();
    }

    void post(Session who, String path, Map<String, Object> body) {
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + path)
                        .header(HttpHeaders.AUTHORIZATION, who.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(body)))
                .hasStatus2xxSuccessful();
    }

    List<String> actions(JsonNode trail) {
        return trail.valueStream().map(n -> n.get("action").asString()).toList();
    }

    @Test
    void staffSeeEveryChangeInOrderWithActorsAndValues() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/tickets/" + key + "/audit")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange();
        assertThat(result).hasStatusOk();
        JsonNode trail = Api.read(result);
        assertThat(actions(trail))
                .containsExactly(
                        "ticket.created",
                        "ticket.assigned",
                        "ticket.status_changed",
                        "ticket.queue_changed",
                        "ticket.comment_added",
                        "ticket.status_changed",
                        "ticket.comment_added",
                        "ticket.priority_changed",
                        "sla.timer_rescheduled",
                        "sla.timer_rescheduled",
                        "ticket.edited");
        JsonNode assigned = trail.get(1);
        assertThat(assigned.get("actor").get("display_name").asString()).startsWith("Agent");
        assertThat(assigned.get("before").isNull()).isTrue();
        assertThat(assigned.get("after").get("display_name").asString()).startsWith("Agent");
        assertThat(trail.get(0).get("actor").get("id").asString())
                .isEqualTo(requester.userId().toString());
        assertThat(trail.get(7).get("before").asString()).isEqualTo("P3_MEDIUM");
        assertThat(trail.get(7).get("after").asString()).isEqualTo("P4_LOW");
        assertThat(trail.get(8).get("field").asString()).isEqualTo("first_response_due_at");
        assertThat(trail.get(9).get("field").asString()).isEqualTo("resolution_due_at");
        assertThat(trail.get(10).get("field").asString()).isEqualTo("title");
        assertThat(trail.get(10).get("after").asString()).isEqualTo("Workstation slow to start after the update");
    }

    @Test
    void requestersSeeOnlyWhatTheyCouldHaveObserved() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/tickets/" + key + "/audit")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(result).hasStatusOk();
        JsonNode trail = Api.read(result);
        assertThat(actions(trail))
                .containsExactly(
                        "ticket.created",
                        "ticket.assigned",
                        "ticket.status_changed",
                        "ticket.status_changed",
                        "ticket.comment_added",
                        "ticket.priority_changed",
                        "ticket.edited");
        assertThat(result)
                .bodyText()
                .doesNotContain("queue_changed")
                .doesNotContain("INTERNAL")
                .doesNotContain("antivirus");
        assertThat(trail.get(1).get("after").get("display_name").asString()).startsWith("Agent");
    }

    @Test
    void someoneElsesTrailIs404() {
        Session stranger = api.loginAs(Role.REQUESTER);
        assertThat(mvc.get()
                        .uri("/api/v1/tickets/" + key + "/audit")
                        .header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void auditRowsCanNeverBeUpdatedOrDeletedEvenBySql() {
        Long id = jdbc.sql(
                        "select min(a.id) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ?")
                .param(key)
                .query(Long.class)
                .single();
        assertThatThrownBy(() -> jdbc.sql("update audit_events set action = 'tampered' where id = ?")
                        .param(id)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_events where id = ?")
                        .param(id)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.sql("select action from audit_events where id = ?")
                        .param(id)
                        .query(String.class)
                        .single())
                .isEqualTo("ticket.created");
    }
}
