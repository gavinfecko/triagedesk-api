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
class SearchTicketsTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    Session rosa;
    Session kim;
    Session agent;
    String printerP2;
    String ehrP1;
    String vpnP3;
    String kimsP1;

    @BeforeEach
    void seedAFewTickets() {
        api = new Api(mvc, jdbc);
        rosa = api.loginAs(Role.REQUESTER);
        kim = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        printerP2 = open(rosa, "Printer jams on labels", Reference.PRINTER, "P2_HIGH");
        ehrP1 = open(rosa, "EHR is down for the whole office", Reference.EHR, "P1_CRITICAL");
        vpnP3 = open(rosa, "VPN drops every ten minutes", Reference.SECURITY_INCIDENT, "P3_MEDIUM");
        kimsP1 = open(kim, "Lost my phone with the MFA app", Reference.SECURITY_INCIDENT, "P1_CRITICAL");
        // the agent takes the EHR outage (NEW → OPEN, assigned) and parks the VPN ticket as PENDING
        post(agent, ehrP1, "/assign", Map.of("assignee_id", "me"));
        post(agent, vpnP3, "/assign", Map.of("assignee_id", "me"));
        post(agent, vpnP3, "/transitions", Map.of("to", "PENDING", "comment", "Which VPN profile do you use?"));
    }

    String open(Session who, String title, UUID category, String priority) {
        MvcTestResult created = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "title",
                        title,
                        "description",
                        "details",
                        "category_id",
                        category.toString(),
                        "priority",
                        priority)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return Api.read(created).get("key").asString();
    }

    void post(Session who, String key, String path, Map<String, Object> body) {
        assertThat(mvc.post()
                        .uri("/api/v1/tickets/" + key + path)
                        .header(HttpHeaders.AUTHORIZATION, who.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(body)))
                .hasStatus2xxSuccessful();
    }

    /** Every query here is scoped to Rosa's or Kim's tickets so other test classes' data cannot interfere. */
    MvcTestResult search(Session who, String params) {
        return mvc.get()
                .uri("/api/v1/tickets?" + params)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    List<String> keys(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        JsonNode items = Api.read(result).get("items");
        return items.valueStream().map(n -> n.get("key").asString()).toList();
    }

    String mine() {
        return "requester_id=" + rosa.userId();
    }

    @Test
    void filtersCombineWithAndAndValuesWithOr() {
        assertThat(keys(search(agent, mine() + "&status=OPEN,PENDING"))).containsExactlyInAnyOrder(ehrP1, vpnP3);
        assertThat(keys(search(agent, mine() + "&status=OPEN,PENDING&priority=P1_CRITICAL")))
                .containsExactly(ehrP1);
        assertThat(keys(search(agent, mine() + "&priority=P2_HIGH,P3_MEDIUM")))
                .containsExactlyInAnyOrder(printerP2, vpnP3);
        assertThat(keys(search(agent, mine() + "&queue_id=" + Reference.HARDWARE_QUEUE)))
                .containsExactly(printerP2);
        assertThat(keys(search(agent, mine() + "&category_id=" + Reference.SECURITY_INCIDENT)))
                .containsExactly(vpnP3);
        assertThat(keys(search(agent, "requester_id=" + kim.userId()))).containsExactly(kimsP1);
    }

    @Test
    void assigneeFilterUnderstandsMeUnassignedAndIds() {
        assertThat(keys(search(agent, mine() + "&assignee_id=me"))).containsExactlyInAnyOrder(ehrP1, vpnP3);
        assertThat(keys(search(agent, mine() + "&assignee_id=unassigned"))).containsExactly(printerP2);
        assertThat(keys(search(agent, mine() + "&assignee_id=" + agent.userId())))
                .containsExactlyInAnyOrder(ehrP1, vpnP3);
        assertThat(search(agent, mine() + "&assignee_id=nobody"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("assignee_id");
    }

    @Test
    void sortIsAllowListedAndRepeatable() {
        assertThat(keys(search(agent, mine() + "&sort=priority,asc&sort=created_at,asc")))
                .containsExactly(ehrP1, printerP2, vpnP3);
        assertThat(keys(search(agent, mine() + "&sort=priority,desc"))).containsExactly(vpnP3, printerP2, ehrP1);
        assertThat(keys(search(agent, mine()))).containsExactly(vpnP3, ehrP1, printerP2); // default: newest first
        MvcTestResult bad = search(agent, mine() + "&sort=password_hash,asc");
        assertThat(bad).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(bad).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(bad).bodyJson().extractingPath("$.errors[0].field").isEqualTo("sort");
        assertThat(search(agent, mine() + "&sort=created_at,sideways")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(search(agent, mine() + "&status=DONE")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pageSizeIsCappedAndPagingWorks() {
        MvcTestResult big = search(agent, mine() + "&size=500");
        assertThat(big).bodyJson().extractingPath("$.page.size").isEqualTo(100);
        assertThat(big).bodyJson().extractingPath("$.page.total_elements").isEqualTo(3);
        MvcTestResult second = search(agent, mine() + "&sort=created_at,asc&size=2&page=1");
        assertThat(keys(second)).containsExactly(vpnP3);
        assertThat(second).bodyJson().extractingPath("$.page.total_pages").isEqualTo(2);
        assertThat(second).bodyJson().extractingPath("$.page.number").isEqualTo(1);
    }

    @Test
    void requestersAreScopedToTheirOwnTicketsWhateverTheyAskFor() {
        assertThat(keys(search(rosa, "priority=P1_CRITICAL"))).containsExactly(ehrP1);
        assertThat(keys(search(rosa, "requester_id=" + kim.userId())))
                .containsExactlyInAnyOrder(vpnP3, ehrP1, printerP2);
        assertThat(keys(search(rosa, "assignee_id=unassigned"))).containsExactly(printerP2);
        assertThat(keys(search(kim, "status=NEW"))).containsExactly(kimsP1);
    }
}
