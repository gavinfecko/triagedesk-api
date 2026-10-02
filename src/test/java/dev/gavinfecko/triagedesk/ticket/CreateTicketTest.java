package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
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
class CreateTicketTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
    }

    static Map<String, Object> printerJam() {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Front desk printer jams on every label");
        body.put("description", "Started this morning; we cannot print patient labels at check-in.");
        body.put("category_id", Reference.PRINTER.toString());
        body.put("priority", "P2_HIGH");
        return body;
    }

    MvcTestResult create(Session who, Map<String, Object> body) {
        return mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    @Test
    void aRequesterOpensANewTicketRoutedByCategoryWithAKeyAndLocation() {
        Session requester = api.loginAs(Role.REQUESTER);
        MvcTestResult result = create(requester, printerJam());
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.key").asString().matches("HD-\\d{6}");
        assertThat(result.getResponse().getHeader("Location")).matches("/api/v1/tickets/HD-\\d{6}");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("NEW");
        assertThat(result).bodyJson().extractingPath("$.priority").isEqualTo("P2_HIGH");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requester.id")
                .isEqualTo(requester.userId().toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requester.display_name")
                .asString()
                .startsWith("Requester");
        assertThat(result).bodyJson().extractingPath("$.queue.name").isEqualTo("Hardware");
        assertThat(result).bodyJson().extractingPath("$.category.name").isEqualTo("Printer");
        assertThat(result).bodyJson().doesNotHavePath("$.warnings");
    }

    @Test
    void keysAreUniqueAndIncreasing() {
        Session requester = api.loginAs(Role.REQUESTER);
        String first = Api.read(create(requester, printerJam())).get("key").asString();
        String second = Api.read(create(requester, printerJam())).get("key").asString();
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void creationIsAuditedAndPublishesTicketCreated() {
        Session requester = api.loginAs(Role.REQUESTER);
        String key = Api.read(create(requester, printerJam())).get("key").asString();
        assertThat(events.stream(TicketCreated.class).filter(e -> e.key().equals(key)))
                .hasSize(1);
        Integer audits = jdbc.sql("""
                        select count(*) from audit_events a join tickets t on t.id = a.ticket_id
                        where t.ticket_key = ? and a.action = 'ticket.created' and a.actor_id = ?""")
                .params(key, requester.userId())
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void staffOpenATicketOnSomeonesBehalfInAChosenQueue() {
        Session agent = api.loginAs(Role.AGENT);
        UUID nurse = api.createUser(Role.REQUESTER);
        Map<String, Object> body = printerJam();
        body.put("requester_id", nurse.toString());
        body.put("queue_id", Reference.FRONT_DESK.toString());
        MvcTestResult result = create(agent, body);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.requester.id").isEqualTo(nurse.toString());
        assertThat(result).bodyJson().extractingPath("$.queue.name").isEqualTo("Front Desk");
    }

    @Test
    void aRequestersStaffOnlyFieldsAreIgnoredWithWarnings() {
        Session requester = api.loginAs(Role.REQUESTER);
        UUID someoneElse = api.createUser(Role.REQUESTER);
        Map<String, Object> body = printerJam();
        body.put("requester_id", someoneElse.toString());
        body.put("queue_id", Reference.NETWORK.toString());
        MvcTestResult result = create(requester, body);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requester.id")
                .isEqualTo(requester.userId().toString());
        assertThat(result).bodyJson().extractingPath("$.queue.name").isEqualTo("Hardware");
        assertThat(result).bodyJson().extractingPath("$.warnings").asArray().hasSize(2);
    }

    @Test
    void invalidOrUnknownFieldsAreValidationProblems() {
        Session agent = api.loginAs(Role.AGENT);
        MvcTestResult tooShort = create(agent, Map.of("title", "Hi", "description", "", "priority", "P9"));
        assertThat(tooShort).hasStatus(HttpStatus.BAD_REQUEST);

        Map<String, Object> missing = printerJam();
        missing.remove("category_id");
        missing.put("title", "x");
        assertThat(create(agent, missing))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("title", "category_id");

        Map<String, Object> unknownCategory = printerJam();
        unknownCategory.put("category_id", UUID.randomUUID().toString());
        assertThat(create(agent, unknownCategory))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("category_id");

        Map<String, Object> unknownRequester = printerJam();
        unknownRequester.put("requester_id", UUID.randomUUID().toString());
        assertThat(create(agent, unknownRequester))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("requester_id");

        Map<String, Object> unknownQueue = printerJam();
        unknownQueue.put("queue_id", UUID.randomUUID().toString());
        assertThat(create(agent, unknownQueue))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("queue_id");
    }

    @Test
    void referenceDataListsCategoriesWithTheirQueuesAndTheQueues() {
        Session requester = api.loginAs(Role.REQUESTER);
        MvcTestResult categories = mvc.get()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(categories).hasStatusOk();
        assertThat(categories)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .contains(
                        "EHR",
                        "Printer",
                        "Network/VPN",
                        "Email/M365",
                        "Hardware",
                        "Access request",
                        "Security incident",
                        "Other");
        assertThat(categories)
                .bodyJson()
                .extractingPath("$[?(@.name == 'EHR')].default_queue_id")
                .asArray()
                .containsExactly(Reference.CLINICAL_SYSTEMS.toString());
        MvcTestResult queues = mvc.get()
                .uri("/api/v1/queues")
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .exchange();
        assertThat(queues)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Clinical Systems", "Front Desk", "Hardware", "Network");
    }
}
