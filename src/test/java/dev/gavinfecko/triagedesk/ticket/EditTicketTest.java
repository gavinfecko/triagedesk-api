package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class EditTicketTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

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
                        "Monitor at reception flickers",
                        "description",
                        "Second monitor only.",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P4_LOW")))
                .exchange();
        key = Api.read(created).get("key").asString();
    }

    MvcTestResult get(Session who) {
        return mvc.get()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    MvcTestResult patch(Session who, String ifMatch, Map<String, Object> body) {
        var request = mvc.patch()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body));
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    String etag(MvcTestResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    @Test
    void readsCarryAnEtagAndEditsMustSendItBack() {
        MvcTestResult read = get(requester);
        assertThat(etag(read)).isEqualTo("\"0\"");
        assertThat(read).bodyJson().extractingPath("$.version").isEqualTo(0);

        MvcTestResult noHeader = patch(requester, null, Map.of("title", "Reception monitor flickers constantly"));
        assertThat(noHeader).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(noHeader).bodyJson().extractingPath("$.type").isEqualTo("/problems/precondition-required");

        MvcTestResult edited = patch(requester, "\"0\"", Map.of("title", "Reception monitor flickers constantly"));
        assertThat(edited).hasStatusOk();
        assertThat(etag(edited)).isEqualTo("\"1\"");
        assertThat(edited).bodyJson().extractingPath("$.title").isEqualTo("Reception monitor flickers constantly");
        assertThat(etag(get(requester))).isEqualTo("\"1\"");

        MvcTestResult stale = patch(requester, "\"0\"", Map.of("description", "Both monitors now."));
        assertThat(stale).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(stale).bodyJson().extractingPath("$.type").isEqualTo("/problems/precondition-failed");

        assertThat(patch(requester, "W/\"1\"", Map.of("description", "Both monitors now.")))
                .hasStatusOk();
        assertThat(etag(get(requester))).isEqualTo("\"2\"");
        Integer edits = jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.edited'")
                .param(key)
                .query(Integer.class)
                .single();
        assertThat(edits).isEqualTo(2);
    }

    @Test
    void anUnchangedEditDoesNotBumpTheVersion() {
        assertThat(patch(agent, "\"0\"", Map.of("title", "Monitor at reception flickers")))
                .hasStatusOk();
        assertThat(etag(get(agent))).isEqualTo("\"0\"");
    }

    @Test
    void requestersMayEditOnlyWhileNewStaffAnyOpenTicket() {
        mvc.post()
                .uri("/api/v1/tickets/" + key + "/assign")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee_id\":\"me\"}")
                .exchange();
        String current = etag(get(agent));
        assertThat(patch(requester, current, Map.of("title", "Trying to edit after pickup")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patch(agent, current, Map.of("title", "Reception monitor: flicker, left screen")))
                .hasStatusOk();

        mvc.post()
                .uri("/api/v1/tickets/" + key + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"RESOLVED\",\"comment\":\"Replaced the cable.\"}")
                .exchange();
        mvc.post()
                .uri("/api/v1/tickets/" + key + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"CLOSED\"}")
                .exchange();
        assertThat(patch(agent, etag(get(agent)), Map.of("title", "Editing a closed ticket")))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void validationAndVisibility() {
        assertThat(patch(agent, "\"0\"", Map.of("title", "tiny")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("title");
        assertThat(patch(agent, "\"0\"", Map.of())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(patch(agent, "not-a-version", Map.of("title", "A valid new title")))
                .hasStatus(HttpStatus.PRECONDITION_FAILED);
        Session stranger = api.loginAs(Role.REQUESTER);
        assertThat(patch(stranger, "\"0\"", Map.of("title", "A valid new title")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void twoConcurrentEditsWithTheSameEtagLetExactlyOneThrough() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (String title : List.of("First writer's title here", "Second writer's title here")) {
                results.add(pool.submit(() -> {
                    start.await();
                    return patch(agent, "\"0\"", Map.of("title", title))
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(200, 412);
            assertThat(etag(get(agent))).isEqualTo("\"1\"");
        } finally {
            pool.shutdownNow();
        }
    }
}
