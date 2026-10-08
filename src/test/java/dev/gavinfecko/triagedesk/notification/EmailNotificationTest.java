package dev.gavinfecko.triagedesk.notification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.AutoCloser;
import dev.gavinfecko.triagedesk.sla.application.BreachScanner;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End to end through Mailpit: the API changes a ticket, Mailpit's HTTP API shows what arrived and for whom. */
@ApiTest
class EmailNotificationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BreachScanner scanner;

    @Autowired
    AutoCloser closer;

    @Autowired
    Clock clock;

    @Value("${test.mailpit.api}")
    String mailpit;

    Api api;
    Session requester;
    Session agent;
    Session admin;
    String key;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        requester = api.loginAs(Role.REQUESTER);
        agent = api.loginAs(Role.AGENT);
        admin = api.loginAs(Role.ADMIN);
        MvcTestResult created = post(
                requester,
                "/api/v1/tickets",
                Map.of(
                        "title", "Exam room 3 PC will not boot",
                        "description", "Black screen after the logo since 8am.",
                        "category_id", Reference.PRINTER,
                        "priority", "P3_MEDIUM"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        key = Api.read(created).get("key").asString();
    }

    MvcTestResult post(Session who, String path, Map<String, Object> body) {
        return mvc.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    void ok(MvcTestResult result) {
        assertThat(result).hasStatus2xxSuccessful();
    }

    void comment(Session who, String visibility, String body) {
        ok(post(who, "/api/v1/tickets/" + key + "/comments", Map.of("visibility", visibility, "body", body)));
    }

    void transition(Session who, String to, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        if (comment != null) {
            body.put("comment", comment);
        }
        ok(post(who, "/api/v1/tickets/" + key + "/transitions", body));
    }

    /** Subjects of every message sent to this address, oldest first. */
    List<String> subjectsFor(String address) {
        JsonNode found = get("/api/v1/search?query="
                + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8) + "&limit=200");
        List<String> subjects = new ArrayList<>();
        found.get("messages")
                .valueStream()
                .forEach(m -> subjects.add(0, m.get("Subject").asString()));
        return subjects;
    }

    JsonNode message(String address, String subjectPart) {
        JsonNode found = get("/api/v1/search?query="
                + URLEncoder.encode("to:\"" + address + "\" subject:\"" + subjectPart + "\"", StandardCharsets.UTF_8));
        String id = found.get("messages").get(0).get("ID").asString();
        return get("/api/v1/message/" + id);
    }

    JsonNode get(String path) {
        try {
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(mailpit + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Waits (mail goes out asynchronously) until at least {@code count} messages reached the address. */
    List<String> await(String address, int count) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        List<String> subjects = subjectsFor(address);
        while (subjects.size() < count && System.nanoTime() < deadline) {
            Thread.onSpinWait();
            subjects = subjectsFor(address);
        }
        return subjects;
    }

    void assign() {
        ok(post(
                admin,
                "/api/v1/tickets/" + key + "/assign",
                Map.of("assignee_id", agent.userId().toString())));
    }

    @Test
    void theRequesterIsToldTheTicketArrivedWithKeyTitleStatusAndLink() {
        assertThat(await(requester.email(), 1))
                .containsExactly("[" + key + "] We received your ticket: Exam room 3 PC will not boot");
        JsonNode mail = message(requester.email(), "We received your ticket");
        assertThat(mail.get("Text").asString())
                .contains(key)
                .contains("Status: NEW")
                .contains("http://localhost:4200/tickets/" + key);
        assertThat(mail.get("HTML").asString()).contains("<a href=\"http://localhost:4200/tickets/" + key + "\">");
        assertThat(mail.get("From").get("Address").asString()).isEqualTo("helpdesk@triagedesk.local");
    }

    @Test
    void assignmentRepliesAndResolutionReachTheOtherParty() {
        assign();
        assertThat(await(agent.email(), 1))
                .containsExactly("[" + key + "] Assigned to you: Exam room 3 PC will not boot");

        await(requester.email(), 1);
        comment(agent, "INTERNAL", "Probably the SSD again.");
        comment(agent, "PUBLIC", "Can you check whether the power light is on?");
        assertThat(await(requester.email(), 2))
                .as("the public reply arrives; the internal note never does")
                .hasSize(2)
                .anySatisfy(s -> assertThat(s).contains("New reply from"));

        comment(requester, "PUBLIC", "It is on, solid green.");
        assertThat(await(agent.email(), 2))
                .hasSize(2)
                .anySatisfy(s -> assertThat(s).contains("New reply from"));

        transition(agent, "RESOLVED", "Replaced the SSD and reimaged.");
        assertThat(await(requester.email(), 4))
                .as("the resolution note as a reply, and the resolved notice")
                .hasSize(4)
                .contains("[" + key + "] Resolved: please confirm or reopen: Exam room 3 PC will not boot");
        assertThat(message(requester.email(), "Resolved").get("Text").asString())
                .contains("reopen")
                .contains("Status: RESOLVED");
        assertThat(subjectsFor(agent.email()))
                .as("nobody is mailed about their own action")
                .hasSize(2);
    }

    @Test
    void aBreachReachesTheAssigneeAndAdminsAndAnAutoCloseReachesTheRequester() {
        assign();
        await(agent.email(), 1);
        jdbc.sql("""
                        update sla_timers set due_at = ?
                        where kind = 'RESOLUTION' and ticket_id = (select id from tickets where ticket_key = ?)""")
                .params(Timestamp.from(clock.instant().minus(Duration.ofMinutes(5))), key)
                .update();
        scanner.scan();
        String breach = "[" + key + "] SLA breached: Resolution";
        assertThat(await(agent.email(), 2)).anySatisfy(s -> assertThat(s).startsWith(breach));
        assertThat(await(admin.email(), 1)).anySatisfy(s -> assertThat(s).startsWith("[TriageDesk] SLA breached on"));
        assertThat(message(admin.email(), "SLA breached on").get("Text").asString())
                .as("one digest per scan, listing this ticket")
                .contains(key + " Exam room 3 PC will not boot: Resolution")
                .contains("(priority raised)")
                .contains("http://localhost:4200/tickets/" + key);
        assertThat(message(agent.email(), "SLA breached: Resolution")
                        .get("Text")
                        .asString())
                .contains("priority was raised");
        assertThat(subjectsFor(requester.email()))
                .noneSatisfy(s -> assertThat(s).contains("SLA"));

        transition(agent, "RESOLVED", "Replaced the SSD.");
        int before = await(requester.email(), 3).size();
        jdbc.sql("update tickets set resolved_at = ? where ticket_key = ?")
                .params(Timestamp.from(clock.instant().minus(Duration.ofDays(10))), key)
                .update();
        closer.run();
        assertThat(await(requester.email(), before + 1))
                .anySatisfy(s -> assertThat(s).startsWith("[" + key + "] Closed"));
    }
}
