package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.sla.application.CalendarService;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.support.Reference;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class CalendarAdminTest {

    static final Map<String, List<String>> CLINIC_HOURS = Map.of(
            "MON", List.of("08:00", "18:00"),
            "TUE", List.of("08:00", "18:00"),
            "WED", List.of("08:00", "18:00"),
            "THU", List.of("08:00", "18:00"),
            "FRI", List.of("08:00", "18:00"));

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CalendarService service;

    Api api;
    Session admin;
    Session agent;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        admin = api.loginAs(Role.ADMIN);
        agent = api.loginAs(Role.AGENT);
    }

    static Map<String, Object> clinic(Map<String, List<String>> hours, String zone) {
        return Map.of(
                "name",
                "Clinic hours",
                "zone",
                zone,
                "hours",
                hours,
                "holidays",
                List.of(
                        Map.of("date", "2026-12-25", "name", "Christmas Day"),
                        Map.of("date", "2026-11-26", "name", "Thanksgiving Day")));
    }

    MvcTestResult put(Session who, Object calendarId, Map<String, Object> body) {
        return mvc.put()
                .uri("/api/v1/sla/calendars/" + calendarId)
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
    void theSeededCalendarsAreThereAndTheClinicOneCountsBusinessHours() {
        MvcTestResult list = get(agent, "/api/v1/sla/calendars");
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$[*].name").asArray().contains("Clinic hours", "24x7");

        MvcTestResult clinic = get(agent, "/api/v1/sla/calendars/" + Reference.CLINIC_CALENDAR);
        assertThat(clinic).hasStatusOk();
        assertThat(clinic).bodyJson().extractingPath("$.zone").isEqualTo("America/New_York");
        assertThat(clinic).bodyJson().extractingPath("$.always_open").isEqualTo(false);
        assertThat(clinic).bodyJson().extractingPath("$.hours.MON").asArray().containsExactly("08:00", "18:00");
        assertThat(clinic)
                .bodyJson()
                .extractingPath("$.holidays[*].name")
                .asArray()
                .contains("Thanksgiving Day", "Christmas Day");

        BusinessCalendar cal = service.calendar(Reference.CLINIC_CALENDAR);
        Instant wednesdayNoonNewYork = Instant.parse("2026-10-07T16:00:00Z");
        assertThat(cal.elapsed(wednesdayNoonNewYork, wednesdayNoonNewYork.plus(Duration.ofHours(24))))
                .isEqualTo(Duration.ofHours(10));
        assertThat(service.calendar(Reference.ALWAYS_OPEN_CALENDAR).alwaysOpen())
                .isTrue();
    }

    @Test
    void anAdminEditsHoursAndHolidaysAndItIsAudited() {
        Map<String, List<String>> shorterWeek =
                Map.of("MON", List.of("07:30", "17:00"), "SAT", List.of("09:00", "12:00"));
        MvcTestResult edited = put(admin, Reference.CLINIC_CALENDAR, clinic(shorterWeek, "America/Chicago"));
        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.hours.SAT").asArray().containsExactly("09:00", "12:00");
        assertThat(edited).bodyJson().extractingPath("$.hours").asMap().doesNotContainKey("TUE");
        assertThat(edited).bodyJson().extractingPath("$.zone").isEqualTo("America/Chicago");
        assertThat(edited).bodyJson().extractingPath("$.holidays[0].name").isEqualTo("Thanksgiving Day");

        Integer audits = jdbc.sql(
                        "select count(*) from audit_events where action = 'sla.calendar_changed' and actor_id = ?")
                .param(admin.userId())
                .query(Integer.class)
                .single();
        assertThat(audits).isEqualTo(1);

        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(CLINIC_HOURS, "America/New_York")))
                .hasStatusOk();
    }

    @Test
    void badZonesHoursAndRolesAreRefused() {
        Map<String, List<String>> monday = Map.of("MON", List.of("08:00", "18:00"));
        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(monday, "Mars/Olympus")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("zone");
        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(Map.of("MON", List.of("18:00", "08:00")), "UTC")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("hours");
        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(Map.of("FUNDAY", List.of("08:00", "18:00")), "UTC")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(Map.of("MON", List.of("8am", "6pm")), "UTC")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(put(admin, Reference.CLINIC_CALENDAR, clinic(Map.of(), "UTC")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(put(agent, Reference.CLINIC_CALENDAR, clinic(monday, "UTC"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(put(admin, Reference.ALWAYS_OPEN_CALENDAR, clinic(monday, "UTC")))
                .as("the 24x7 calendar is not editable")
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get(agent, "/api/v1/sla/calendars/00000000-0000-4000-8000-000000000399"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
