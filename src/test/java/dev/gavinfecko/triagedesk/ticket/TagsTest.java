package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.util.List;
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

@ApiTest
class TagsTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    Session requester;
    Session agent;
    String key;
    String unique; // a tag name only this test run uses, so counts are deterministic

    @BeforeEach
    void openATicket() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        unique = "run-" + UUID.randomUUID().toString().substring(0, 8);
        key = open("Printer in nurse station prints blank pages");
    }

    String open(String title) {
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        title,
                        "description",
                        "details",
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P3_MEDIUM")))
                .exchange();
        return Api.read(created).get("key").asString();
    }

    MvcTestResult put(Session who, String ticket, List<String> tags) {
        return mvc.put()
                .uri("/api/v1/tickets/" + ticket + "/tags")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(tags))
                .exchange();
    }

    @Test
    void anAgentTagsATicketNamesAreNormalisedAndTheSetIsReplaced() {
        MvcTestResult tagged = put(agent, key, List.of("  Recurring ", "VENDOR", unique));
        assertThat(tagged).hasStatusOk();
        assertThat(tagged).bodyJson().extractingPath("$.tags").asArray().containsExactly("recurring", unique, "vendor");

        MvcTestResult replaced = put(agent, key, List.of("vendor", unique));
        assertThat(replaced).bodyJson().extractingPath("$.tags").asArray().containsExactly(unique, "vendor");
        assertThat(put(agent, key, List.of()))
                .bodyJson()
                .extractingPath("$.tags")
                .asArray()
                .isEmpty();

        Integer audits = jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.tags_changed'")
                .param(key)
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(3);
        String last = jdbc.sql(
                        "select before_value::text from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.tags_changed' order by a.id desc limit 1")
                .param(key)
                .query(String.class)
                .single();
        assertThat(last).contains("vendor").contains(unique);
    }

    @Test
    void sameTagsAgainChangeNothing() {
        put(agent, key, List.of(unique));
        put(agent, key, List.of(unique));
        Integer audits = jdbc.sql(
                        "select count(*) from audit_events a join tickets t on t.id = a.ticket_id where t.ticket_key = ? and a.action = 'ticket.tags_changed'")
                .param(key)
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void tagsListUsageCountsAndTheListFiltersByTag() {
        String second = open("Scanner saves documents to the wrong folder");
        put(agent, key, List.of(unique, unique + "-only-here"));
        put(agent, second, List.of(unique));

        MvcTestResult tags = mvc.get()
                .uri("/api/v1/tags")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(tags).hasStatusOk();
        assertThat(tags)
                .bodyJson()
                .extractingPath("$[?(@.name == '" + unique + "')].count")
                .asArray()
                .containsExactly(2);
        assertThat(tags)
                .bodyJson()
                .extractingPath("$[?(@.name == '" + unique + "-only-here')].count")
                .asArray()
                .containsExactly(1);

        MvcTestResult filtered = mvc.get()
                .uri("/api/v1/tickets?tag=" + unique + "&size=100")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange();
        assertThat(filtered)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactlyInAnyOrder(key, second);
        MvcTestResult one = mvc.get()
                .uri("/api/v1/tickets?tag=" + unique + "-only-here&size=100")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange();
        assertThat(one).bodyJson().extractingPath("$.items[*].key").asArray().containsExactly(key);
        MvcTestResult none = mvc.get()
                .uri("/api/v1/tickets?tag=never-used-" + unique)
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .exchange();
        assertThat(none)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.page.total_elements")
                .isEqualTo(0);
    }

    @Test
    void requestersSeeTagsButCannotWriteThem() {
        put(agent, key, List.of(unique));
        MvcTestResult read = mvc.get()
                .uri("/api/v1/tickets/" + key)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(read).bodyJson().extractingPath("$.tags").asArray().containsExactly(unique);
        assertThat(put(requester, key, List.of("mine"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void badNamesAndClosedTicketsAreRefused() {
        for (String bad : List.of("x", "a".repeat(31), "has space", "-leading", "trailing-", "émoji")) {
            MvcTestResult result = put(agent, key, List.of(bad));
            assertThat(result).as(bad).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("tags");
        }
        mvc.post()
                .uri("/api/v1/tickets/" + key + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"CANCELLED\"}")
                .exchange();
        assertThat(put(agent, key, List.of(unique))).hasStatus(HttpStatus.CONFLICT);
    }
}
