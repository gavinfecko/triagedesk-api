package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
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
class TransitionsTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    Api api;
    Session requester;
    Session agent;
    Session admin;
    String key;

    @BeforeEach
    void openATicket() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        admin = api.loginAs(Role.ADMIN);
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        "VPN drops every ten minutes",
                        "description",
                        "From home, since Tuesday.",
                        "category_id",
                        Reference.SECURITY_INCIDENT.toString(),
                        "priority",
                        "P3_MEDIUM")))
                .exchange();
        key = Api.read(created).get("key").asString();
    }

    MvcTestResult move(Session who, String to, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        if (comment != null) {
            body.put("comment", comment);
        }
        return mvc.post()
                .uri("/api/v1/tickets/" + key + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    Map<String, Object> row() {
        return jdbc.sql("select status, first_responded_at, resolved_at, closed_at from tickets where ticket_key = ?")
                .param(key)
                .query()
                .singleRow();
    }

    @Test
    void theHappyPathSetsEveryLifecycleTimestampAndPublishesOneEventPerMove() {
        assertThat(move(agent, "OPEN", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("OPEN");
        assertThat(row().get("first_responded_at")).isNotNull();

        assertThat(move(agent, "PENDING", "Can you confirm which VPN profile you use?"))
                .hasStatusOk();
        assertThat(row().get("status")).isEqualTo("PENDING");

        assertThat(move(requester, "OPEN", null)).hasStatusOk();
        assertThat(move(agent, "RESOLVED", "Updated the VPN client; drops stopped on our side."))
                .hasStatusOk();
        assertThat(row().get("resolved_at")).isNotNull();
        assertThat(row().get("closed_at")).isNull();

        assertThat(move(requester, "CLOSED", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CLOSED");
        assertThat(row().get("closed_at")).isNotNull();

        assertThat(events.stream(TicketStatusChanged.class).filter(e -> e.key().equals(key)))
                .hasSize(5);
        Integer audits = jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.status_changed'")
                .param(key)
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(5);
        MvcTestResult comments = mvc.get()
                .uri("/api/v1/tickets/" + key + "/comments")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(comments)
                .bodyJson()
                .extractingPath("$[*].body")
                .asArray()
                .hasSize(2); // the two required comments, PUBLIC
    }

    @Test
    void pendingAndResolvedRequireAComment() {
        move(agent, "OPEN", null);
        MvcTestResult noComment = move(agent, "PENDING", null);
        assertThat(noComment).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noComment).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(noComment).bodyJson().extractingPath("$.errors[0].field").isEqualTo("comment");
        assertThat(move(agent, "RESOLVED", "   ")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(row().get("status")).isEqualTo("OPEN");
    }

    @Test
    void movesOutsideTheTableAre409NamingTheCurrentStatus() {
        MvcTestResult requesterOpens = move(requester, "OPEN", null);
        assertThat(requesterOpens).hasStatus(HttpStatus.CONFLICT);
        assertThat(requesterOpens).bodyJson().extractingPath("$.type").isEqualTo("/problems/ticket-state-conflict");
        assertThat(requesterOpens)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("is NEW")
                .contains("requester");

        assertThat(move(agent, "RESOLVED", "skipping OPEN"))
                .hasStatus(HttpStatus.CONFLICT); // NEW -> RESOLVED is not a move
        move(agent, "OPEN", null);
        assertThat(move(agent, "CANCELLED", null))
                .hasStatus(HttpStatus.CONFLICT); // only an admin cancels an OPEN ticket
        assertThat(move(requester, "CANCELLED", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(move(admin, "CANCELLED", "Duplicate of another ticket")).hasStatusOk();
        MvcTestResult afterTerminal = move(admin, "OPEN", null);
        assertThat(afterTerminal).hasStatus(HttpStatus.CONFLICT);
        assertThat(afterTerminal)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("CANCELLED")
                .contains("closed to further changes");
    }

    @Test
    void aRequesterMayCancelOnlyWhileNewAndMayReopenAResolvedTicket() {
        assertThat(move(requester, "CANCELLED", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");

        openATicket();
        move(agent, "OPEN", null);
        move(agent, "RESOLVED", "Done.");
        assertThat(row().get("resolved_at")).isNotNull();
        assertThat(move(requester, "OPEN", "Still dropping, sorry.")).hasStatusOk();
        assertThat(row().get("status")).isEqualTo("OPEN");
        assertThat(row().get("resolved_at")).isNull();
    }

    @Test
    void someoneElsesTicketIs404AndAnUnknownStatusIs400() {
        Session stranger = api.loginAs(Role.REQUESTER);
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/transitions")
                        .header(HttpHeaders.AUTHORIZATION, stranger.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"CANCELLED\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/transitions")
                        .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"DONE\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(row().get("status")).isEqualTo("NEW");
        assertThat(row().get("first_responded_at")).as("untouched").isNull();
        assertThat(Timestamp.class).isNotNull();
    }
}
