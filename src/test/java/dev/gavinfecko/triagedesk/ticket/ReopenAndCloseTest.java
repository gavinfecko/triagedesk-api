package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ReopenAndCloseTest {

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
    void resolveATicket() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        "E-prescribing fails with a certificate error",
                        "description",
                        "Since this morning.",
                        "category_id",
                        Reference.EHR.toString(),
                        "priority",
                        "P2_HIGH")))
                .exchange();
        key = Api.read(created).get("key").asString();
        move(agent, "OPEN", null);
        move(agent, "RESOLVED", "Renewed the certificate.");
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
        return jdbc.sql("select status, resolved_at, closed_at, reopen_count from tickets where ticket_key = ?")
                .param(key)
                .query()
                .singleRow();
    }

    @Test
    void theRequesterConfirmsTheFixAndTheTicketCloses() {
        MvcTestResult closed = move(requester, "CLOSED", null);
        assertThat(closed).hasStatusOk();
        assertThat(closed).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
        assertThat(closed).bodyJson().extractingPath("$.closed_at").asString().isNotBlank();
        assertThat(row().get("closed_at")).isNotNull();
        assertThat(events.stream(TicketStatusChanged.class)
                        .filter(e -> e.key().equals(key) && e.to().name().equals("CLOSED")))
                .hasSize(1);
    }

    @Test
    void theRequesterReopensWithinTheWindowAndTheCountGrows() {
        MvcTestResult reopened = move(requester, "OPEN", "It failed again on the second prescription.");
        assertThat(reopened).hasStatusOk();
        assertThat(reopened).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
        assertThat(reopened).bodyJson().extractingPath("$.reopen_count").isEqualTo(1);
        assertThat(reopened).bodyJson().extractingPath("$.resolved_at").isNull();
        assertThat(row().get("resolved_at")).isNull();
        assertThat(events.stream(TicketStatusChanged.class)
                        .filter(e -> e.key().equals(key)
                                && e.from().name().equals("RESOLVED")
                                && e.to().name().equals("OPEN")))
                .hasSize(1);

        move(agent, "RESOLVED", "Second attempt.");
        assertThat(move(requester, "OPEN", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.reopen_count")
                .isEqualTo(2);
    }

    @Test
    void afterFourteenDaysReopeningIsRefusedWithAHintToOpenANewTicket() {
        jdbc.sql("update tickets set resolved_at = ? where ticket_key = ?")
                .params(Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)), key)
                .update();
        MvcTestResult late = move(requester, "OPEN", "Still broken");
        assertThat(late).hasStatus(HttpStatus.CONFLICT);
        assertThat(late).bodyJson().extractingPath("$.type").isEqualTo("/problems/reopen-window-closed");
        assertThat(late)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("new ticket")
                .contains(key);
        assertThat(row().get("status")).isEqualTo("RESOLVED");
        assertThat(move(agent, "OPEN", null)).hasStatus(HttpStatus.CONFLICT); // the window binds staff too
        assertThat(move(requester, "CLOSED", null)).hasStatusOk(); // closing is always allowed
    }

    @Test
    void exactlyFourteenDaysIsStillInsideTheWindow() {
        jdbc.sql("update tickets set resolved_at = ? where ticket_key = ?")
                .params(Timestamp.from(Instant.now().minus(14, ChronoUnit.DAYS).plusSeconds(60)), key)
                .update();
        assertThat(move(requester, "OPEN", null)).hasStatusOk();
    }
}
