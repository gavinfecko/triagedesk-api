package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyService;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
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
class SlaPolicyAdminTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SlaPolicyService service;

    Api api;
    Session admin;
    Session agent;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        admin = api.loginAs(Role.ADMIN);
        agent = api.loginAs(Role.AGENT);
    }

    MvcTestResult patch(Session who, UUID id, Map<String, Object> body) {
        return mvc.patch()
                .uri("/api/v1/sla/policies/" + id)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(body))
                .exchange();
    }

    MvcTestResult get(Session who, String path) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    @Test
    void theFourSeededPoliciesMatchTheArchitectureDefaults() {
        MvcTestResult list = get(agent, "/api/v1/sla/policies");
        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$[*].priority")
                .asArray()
                .containsExactly("P1_CRITICAL", "P2_HIGH", "P3_MEDIUM", "P4_LOW");
        assertThat(list)
                .bodyJson()
                .extractingPath("$[*].first_response_minutes")
                .asArray()
                .containsExactly(15, 60, 240, 480);
        assertThat(list)
                .bodyJson()
                .extractingPath("$[*].resolution_minutes")
                .asArray()
                .containsExactly(240, 480, 1800, 3000);
        assertThat(list)
                .bodyJson()
                .extractingPath("$[*].calendar_name")
                .asArray()
                .containsExactly("24x7", "Clinic hours", "Clinic hours", "Clinic hours");
        assertThat(list).bodyJson().extractingPath("$[*].active").asArray().containsExactly(true, true, true, true);

        MvcTestResult p1 = get(agent, "/api/v1/sla/policies/" + Reference.POLICY_P1);
        assertThat(p1).hasStatusOk();
        assertThat(p1).bodyJson().extractingPath("$.calendar_id").isEqualTo(Reference.ALWAYS_OPEN_CALENDAR.toString());

        assertThat(service.activeFor(Priority.P3_MEDIUM)).hasValueSatisfying(p -> {
            assertThat(p.resolutionMinutes()).isEqualTo(1800);
            assertThat(p.calendarId()).isEqualTo(Reference.CLINIC_CALENDAR);
        });
        assertThat(get(agent, "/api/v1/sla/policies/" + UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anAdminChangesBudgetsCalendarAndActiveFlagAndEachEditIsAudited() {
        MvcTestResult edited = patch(
                admin,
                Reference.POLICY_P2,
                Map.of("resolution_minutes", 600, "calendar_id", Reference.ALWAYS_OPEN_CALENDAR));
        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.first_response_minutes").isEqualTo(60);
        assertThat(edited).bodyJson().extractingPath("$.resolution_minutes").isEqualTo(600);
        assertThat(edited).bodyJson().extractingPath("$.calendar_name").isEqualTo("24x7");
        assertThat(edited).bodyJson().extractingPath("$.active").isEqualTo(true);

        MvcTestResult paused = patch(admin, Reference.POLICY_P4, Map.of("active", false));
        assertThat(paused).hasStatusOk();
        assertThat(paused).bodyJson().extractingPath("$.active").isEqualTo(false);
        assertThat(service.activeFor(Priority.P4_LOW)).isEmpty();

        Integer audits = jdbc.sql(
                        "select count(*) from audit_events where action = 'sla.policy_changed' and actor_id = ?")
                .param(admin.userId())
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(2);
        String field = jdbc.sql("select field from audit_events where action = 'sla.policy_changed' and actor_id = ? "
                        + "order by created_at limit 1")
                .param(admin.userId())
                .query(String.class)
                .single();
        assertThat(field).isEqualTo("policy:P2_HIGH");

        assertThat(patch(
                        admin,
                        Reference.POLICY_P2,
                        Map.of("resolution_minutes", 480, "calendar_id", Reference.CLINIC_CALENDAR)))
                .hasStatusOk();
        assertThat(patch(admin, Reference.POLICY_P4, Map.of("active", true))).hasStatusOk();
        assertThat(service.activeFor(Priority.P4_LOW)).isPresent();
    }

    @Test
    void badBudgetsCalendarsBodiesAndRolesAreRefused() {
        assertThat(patch(admin, Reference.POLICY_P2, Map.of("resolution_minutes", 60)))
                .as("resolution equal to first response")
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("resolution_minutes");
        assertThat(patch(admin, Reference.POLICY_P2, Map.of("first_response_minutes", 500)))
                .as("first response pushed past the resolution budget")
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("resolution_minutes");
        assertThat(patch(admin, Reference.POLICY_P2, Map.of("first_response_minutes", 0)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("first_response_minutes");
        assertThat(patch(admin, Reference.POLICY_P2, Map.of("calendar_id", UUID.randomUUID())))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("calendar_id");
        assertThat(patch(admin, Reference.POLICY_P2, Map.of())).as("empty body").hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(patch(agent, Reference.POLICY_P2, Map.of("active", false))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patch(admin, UUID.randomUUID(), Map.of("active", false))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get(agent, "/api/v1/sla/policies/" + Reference.POLICY_P2))
                .bodyJson()
                .extractingPath("$.resolution_minutes")
                .as("nothing changed")
                .isEqualTo(480);
    }
}
