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
import tools.jackson.databind.JsonNode;

@ApiTest
class FullTextSearchTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    Session rosa;
    Session agent;
    String word; // a nonsense word unique to this run, so other tests' tickets never match
    String labelsTwice;
    String labelsOnce;
    String unrelated;

    @BeforeEach
    void seed() {
        api = new Api(mvc, jdbc);
        rosa = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        word = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        labelsTwice =
                open("Label printer " + word + " jams", "The " + word + " label printer jams on every label we print.");
        labelsOnce = open("Wristband station problem", "One " + word + " label came out blank this morning.");
        unrelated = open("Wi-Fi drops in exam room 3", "Tablets take a minute to load each chart.");
    }

    String open(String title, String description) {
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, rosa.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        title,
                        "description",
                        description,
                        "category_id",
                        Reference.PRINTER.toString(),
                        "priority",
                        "P3_MEDIUM")))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return Api.read(created).get("key").asString();
    }

    List<String> keys(Session who, String params) {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/tickets?" + params)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
        assertThat(result).hasStatusOk();
        JsonNode items = Api.read(result).get("items");
        return items.valueStream().map(n -> n.get("key").asString()).toList();
    }

    @Test
    void matchesWordsInTitleOrDescriptionRankedByRelevance() {
        assertThat(keys(agent, "q=" + word)).containsExactly(labelsTwice, labelsOnce);
        assertThat(keys(agent, "q=" + word + " label")).containsExactly(labelsTwice, labelsOnce);
        assertThat(keys(agent, "q=blank " + word)).containsExactly(labelsOnce); // AND semantics: both words must appear
        assertThat(keys(agent, "q=printers " + word)).containsExactly(labelsTwice); // stemming: printers ~ printer
    }

    @Test
    void combinesWithFiltersAndAnExplicitSortWins() {
        mvc.post()
                .uri("/api/v1/tickets/" + labelsOnce + "/assign")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee_id\":\"me\"}")
                .exchange();
        assertThat(keys(agent, "q=" + word + "&status=OPEN")).containsExactly(labelsOnce);
        assertThat(keys(agent, "q=" + word + "&assignee_id=unassigned")).containsExactly(labelsTwice);
        assertThat(keys(agent, "q=" + word + "&sort=created_at,asc")).containsExactly(labelsTwice, labelsOnce);
        assertThat(keys(agent, "q=" + word + "&sort=created_at,desc")).containsExactly(labelsOnce, labelsTwice);
        assertThat(keys(agent, "q=" + word + "&size=1")).containsExactly(labelsTwice);
    }

    @Test
    void commentsAreNotSearchedAndRequestersStayScoped() {
        String secret = "xk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mvc.post()
                .uri("/api/v1/tickets/" + unrelated + "/comments")
                .header(HttpHeaders.AUTHORIZATION, agent.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("visibility", "INTERNAL", "body", "note " + secret)))
                .exchange();
        assertThat(keys(agent, "q=" + secret)).isEmpty();

        Session kim = api.loginAs(Role.REQUESTER);
        assertThat(keys(kim, "q=" + word)).isEmpty();
        assertThat(keys(rosa, "q=" + word)).containsExactly(labelsTwice, labelsOnce);
    }

    @Test
    void anOverlongQueryIsRejected() {
        assertThat(mvc.get()
                        .uri("/api/v1/tickets?q=" + "a".repeat(201))
                        .header(HttpHeaders.AUTHORIZATION, agent.bearer()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
