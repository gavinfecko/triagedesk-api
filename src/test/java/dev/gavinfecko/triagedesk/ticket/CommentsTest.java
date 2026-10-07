package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.CommentAdded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketFirstResponded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
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
class CommentsTest {

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
                        "Label printer jams on every label",
                        "description",
                        "Since this morning.",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P2_HIGH")))
                .exchange();
        key = Api.read(created).get("key").asString();
    }

    MvcTestResult comment(Session who, String ticket, String visibility, String body) {
        return mvc.post()
                .uri("/api/v1/tickets/" + ticket + "/comments")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("visibility", visibility, "body", body)))
                .exchange();
    }

    MvcTestResult list(Session who, String ticket) {
        return mvc.get()
                .uri("/api/v1/tickets/" + ticket + "/comments")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    String status() {
        return jdbc.sql("select status from tickets where ticket_key = ?")
                .param(key)
                .query(String.class)
                .single();
    }

    Object firstRespondedAt() {
        return jdbc.sql("select first_responded_at from tickets where ticket_key = ?")
                .param(key)
                .query(java.sql.Timestamp.class)
                .optional()
                .orElse(null);
    }

    @Test
    void staffsFirstPublicReplyRecordsTheFirstResponseOnce() {
        assertThat(firstRespondedAt()).isNull();
        MvcTestResult reply = comment(agent, key, "PUBLIC", "On it. Which tray is it jamming from?");
        assertThat(reply).hasStatus(HttpStatus.CREATED);
        assertThat(reply).bodyJson().extractingPath("$.visibility").isEqualTo("PUBLIC");
        assertThat(reply)
                .bodyJson()
                .extractingPath("$.author.id")
                .isEqualTo(agent.userId().toString());
        assertThat(reply)
                .bodyJson()
                .extractingPath("$.author.display_name")
                .asString()
                .startsWith("Agent");
        Object first = firstRespondedAt();
        assertThat(first).isNotNull();
        assertThat(status()).isEqualTo("NEW"); // assignment opens a ticket (TD-24), a reply does not

        comment(agent, key, "PUBLIC", "Second reply");
        assertThat(firstRespondedAt()).isEqualTo(first);
        assertThat(events.stream(TicketFirstResponded.class).filter(e -> e.key().equals(key)))
                .hasSize(1);
        assertThat(events.stream(CommentAdded.class).filter(e -> e.key().equals(key)))
                .hasSize(2);
    }

    @Test
    void internalNotesNeverReachTheRequester() {
        comment(
                agent,
                key,
                "INTERNAL",
                "Requester's printer is the old Zebra; check the firmware list before replying.");
        comment(agent, key, "PUBLIC", "Looking into it now.");
        comment(requester, key, "PUBLIC", "Thanks!");

        MvcTestResult mine = list(requester, key);
        assertThat(mine).hasStatusOk();
        assertThat(mine).bodyJson().extractingPath("$[*].visibility").asArray().containsExactly("PUBLIC", "PUBLIC");
        assertThat(mine).bodyText().doesNotContain("Zebra").doesNotContain("INTERNAL");

        MvcTestResult staff = list(agent, key);
        assertThat(staff)
                .bodyJson()
                .extractingPath("$[*].visibility")
                .asArray()
                .containsExactly("INTERNAL", "PUBLIC", "PUBLIC");
        assertThat(staff).bodyJson().extractingPath("$[0].body").asString().contains("Zebra");
    }

    @Test
    void aRequesterCannotWriteInternalNotesOrTouchSomeoneElsesTicket() {
        MvcTestResult internal = comment(requester, key, "INTERNAL", "sneaky");
        assertThat(internal)
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/forbidden");

        Session stranger = api.loginAs(Role.REQUESTER);
        assertThat(comment(stranger, key, "PUBLIC", "hello?")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(list(stranger, key)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.sql(
                                "select count(*) from ticket_comments c join tickets t on t.id = c.ticket_id where t.ticket_key = ?")
                        .param(key)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void aRequestersReplyOnAPendingTicketReturnsItToOpenAndIsAudited() {
        jdbc.sql("update tickets set status = 'PENDING' where ticket_key = ?")
                .param(key)
                .update();
        assertThat(comment(requester, key, "PUBLIC", "Tray 2, and it just happened again."))
                .hasStatus(HttpStatus.CREATED);
        assertThat(status()).isEqualTo("OPEN");
        assertThat(events.stream(TicketStatusChanged.class).filter(e -> e.key().equals(key)))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.from().name()).isEqualTo("PENDING");
                    assertThat(e.to().name()).isEqualTo("OPEN");
                });
        Integer audits = jdbc.sql("""
                        select count(*) from audit_events a join tickets t on t.id = a.ticket_id
                        where t.ticket_key = ? and a.action in ('ticket.comment_added', 'ticket.status_changed') and a.actor_id = ?""")
                .params(key, requester.userId())
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(2);

        comment(requester, key, "PUBLIC", "Still open, another reply");
        assertThat(status()).isEqualTo("OPEN");
        assertThat(firstRespondedAt()).isNull(); // requester replies never count as a first response
    }

    @Test
    void emptyOrOversizedBodiesAreValidationProblems() {
        assertThat(comment(agent, key, "PUBLIC", "   "))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("body");
        assertThat(comment(agent, key, "PUBLIC", "x".repeat(10_001))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"no visibility\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
