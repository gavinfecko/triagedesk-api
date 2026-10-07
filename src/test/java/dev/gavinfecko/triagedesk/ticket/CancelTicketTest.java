package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.util.HashMap;
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

@ApiTest
class CancelTicketTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

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
                        "Guest Wi-Fi password for the waiting room",
                        "description",
                        "Patients keep asking.",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P4_LOW")))
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

    String status() {
        return jdbc.sql("select status from tickets where ticket_key = ?")
                .param(key)
                .query(String.class)
                .single();
    }

    @Test
    void aRequesterCancelsTheirOwnNewTicketAndItIsTerminal() {
        MvcTestResult cancelled = move(requester, "CANCELLED", null);
        assertThat(cancelled)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");

        assertThat(move(requester, "OPEN", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(move(admin, "OPEN", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee_id\":\"me\"}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.get().uri("/api/v1/tickets/" + key).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .hasStatusOk();
        Integer audit = jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.status_changed' and a.after_value = '\"CANCELLED\"'::jsonb")
                .param(key)
                .query(Integer.class)
                .single();
        assertThat(audit).isEqualTo(1);
    }

    @Test
    void onceWorkHasStartedOnlyAnAdminCancelsAndMustSayWhy() {
        mvc.post()
                .uri("/api/v1/tickets/" + key + "/assign")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee_id\":\"me\"}")
                .exchange();
        assertThat(status()).isEqualTo("OPEN");

        assertThat(move(requester, "CANCELLED", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(move(agent, "CANCELLED", "duplicate")).hasStatus(HttpStatus.CONFLICT);
        MvcTestResult silent = move(admin, "CANCELLED", null);
        assertThat(silent)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("comment");
        assertThat(status()).isEqualTo("OPEN");

        assertThat(move(admin, "CANCELLED", "Duplicate of HD-001000; tracking it there."))
                .hasStatusOk();
        assertThat(status()).isEqualTo("CANCELLED");
        MvcTestResult comments = mvc.get()
                .uri("/api/v1/tickets/" + key + "/comments")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(comments).bodyJson().extractingPath("$[-1].body").asString().contains("Duplicate of HD-001000");
    }

    @Test
    void anAdminCancelsAPendingTicketWithAReason() {
        mvc.post()
                .uri("/api/v1/tickets/" + key + "/assign")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee_id\":\"me\"}")
                .exchange();
        move(agent, "PENDING", "Which room is the waiting room?");
        assertThat(move(admin, "CANCELLED", "Requester left the practice.")).hasStatusOk();
        assertThat(status()).isEqualTo("CANCELLED");
    }

    @Test
    void someoneElsesTicketIs404() {
        Session stranger = api.loginAs(Role.REQUESTER);
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + "/transitions")
                        .header(HttpHeaders.AUTHORIZATION, stranger.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"CANCELLED\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(status()).isEqualTo("NEW");
    }
}
