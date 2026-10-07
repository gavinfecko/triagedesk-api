package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketAssigned;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.util.Collections;
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
class AssignmentTest {

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
                        "Shared mailbox stopped receiving referrals",
                        "description",
                        "Since yesterday.",
                        "category_id",
                        Reference.EHR.toString(),
                        "priority",
                        "P2_HIGH")))
                .exchange();
        key = Api.read(created).get("key").asString();
    }

    MvcTestResult assign(Session who, Object assigneeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("assignee_id", assigneeId);
        return mvc.post()
                .uri("/api/v1/tickets/" + key + "/assign")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    MvcTestResult queue(Session who, Object queueId) {
        return mvc.post()
                .uri("/api/v1/tickets/" + key + "/queue")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Collections.singletonMap("queue_id", queueId)))
                .exchange();
    }

    Map<String, Object> row() {
        return jdbc.sql("select status, assignee_id, queue_id, first_responded_at from tickets where ticket_key = ?")
                .param(key)
                .query()
                .singleRow();
    }

    @Test
    void takingANewTicketOpensItAndCountsAsTheFirstResponse() {
        MvcTestResult taken = assign(agent, "me");
        assertThat(taken).hasStatusOk();
        assertThat(taken).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
        assertThat(taken)
                .bodyJson()
                .extractingPath("$.assignee.id")
                .isEqualTo(agent.userId().toString());
        assertThat(taken)
                .bodyJson()
                .extractingPath("$.assignee.display_name")
                .asString()
                .startsWith("Agent");
        assertThat(row().get("first_responded_at")).isNotNull();
        assertThat(events.stream(TicketAssigned.class).filter(e -> e.key().equals(key)))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.from()).isNull();
                    assertThat(e.to()).isEqualTo(agent.userId());
                });
        assertThat(events.stream(TicketStatusChanged.class).filter(e -> e.key().equals(key)))
                .hasSize(1);
        assertThat(auditCount("ticket.assigned")).isEqualTo(1);
        assertThat(auditCount("ticket.status_changed")).isEqualTo(1);
    }

    @Test
    void reassigningAndUnassigningKeepTheStatusAndAreAudited() {
        assign(agent, "me");
        UUID colleague = api.createUser(Role.AGENT);
        MvcTestResult handed = assign(agent, colleague.toString());
        assertThat(handed)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.assignee.id")
                .isEqualTo(colleague.toString());
        assertThat(row().get("status")).isEqualTo("OPEN");

        MvcTestResult unassigned = assign(agent, null);
        assertThat(unassigned)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.assignee")
                .isNull();
        assertThat(row().get("status")).isEqualTo("OPEN");
        assertThat(auditCount("ticket.assigned")).isEqualTo(3);
        assertThat(auditCount("ticket.status_changed")).isEqualTo(1);
        String lastBefore = jdbc.sql(
                        "select before_value::text from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.assigned' order by a.id desc limit 1")
                .param(key)
                .query(String.class)
                .single();
        assertThat(lastBefore).isEqualTo("\"" + colleague + "\"");
    }

    @Test
    void onlyActiveAgentsAndAdminsCanBeAssignees() {
        UUID aRequester = api.createUser(Role.REQUESTER);
        MvcTestResult toRequester = assign(agent, aRequester.toString());
        assertThat(toRequester)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/invalid-assignee");

        UUID gone = api.createUser(Role.AGENT);
        jdbc.sql("update users set active = false where id = ?").param(gone).update();
        assertThat(assign(agent, gone.toString()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/invalid-assignee");
        assertThat(assign(agent, UUID.randomUUID().toString())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(assign(agent, "someone"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("assignee_id");
        assertThat(row().get("assignee_id")).isNull();
        assertThat(row().get("status")).isEqualTo("NEW");
    }

    @Test
    void movingQueuesIsAuditedAndValidated() {
        assertThat(row().get("queue_id")).isEqualTo(Reference.CLINICAL_SYSTEMS);
        MvcTestResult moved = queue(agent, Reference.FRONT_DESK.toString());
        assertThat(moved)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.queue.name")
                .isEqualTo("Front Desk");
        assertThat(queue(agent, Reference.FRONT_DESK.toString())).hasStatusOk(); // same queue again: no-op
        assertThat(auditCount("ticket.queue_changed")).isEqualTo(1);
        assertThat(queue(agent, UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("queue_id");
        assertThat(queue(agent, null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requestersGet403AndClosedTicketsGet409() {
        assertThat(assign(requester, "me")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(queue(requester, Reference.NETWORK.toString())).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/transitions")
                        .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"CANCELLED\"}"))
                .hasStatusOk();
        MvcTestResult onCancelled = assign(agent, "me");
        assertThat(onCancelled)
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/ticket-state-conflict");
        assertThat(queue(agent, Reference.NETWORK.toString())).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void theListShowsTheAssigneesName() {
        assign(agent, "me");
        MvcTestResult list = mvc.get()
                .uri("/api/v1/tickets?size=100")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[?(@.key == '" + key + "')].assignee.display_name")
                .asArray()
                .singleElement()
                .asString()
                .startsWith("Agent");
    }

    private int auditCount(String action) {
        return jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = ?")
                .params(key, action)
                .query(Integer.class)
                .single();
    }
}
