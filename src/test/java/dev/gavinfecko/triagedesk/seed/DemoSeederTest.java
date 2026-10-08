package dev.gavinfecko.triagedesk.seed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.sla.application.CalendarService;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyService;
import dev.gavinfecko.triagedesk.sla.application.SlaTimerService;
import dev.gavinfecko.triagedesk.sla.application.TicketSlaView.TimerView;
import dev.gavinfecko.triagedesk.sla.domain.SlaStatus;
import dev.gavinfecko.triagedesk.support.ApiTest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

@ApiTest
class DemoSeederTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    Clock clock;

    @Autowired
    SlaTimerService timers;

    @Autowired
    SlaPolicyService policies;

    @Autowired
    CalendarService calendars;

    @Autowired
    MockMvcTester mvc;

    @Test
    void seedsTheDemoClinicOnceAndNeverDuplicates() {
        DemoSeeder seeder = new DemoSeeder(jdbc, passwords, transactions, clock, timers, policies, calendars);
        DemoSeeder.Result first = seeder.seed();
        DemoSeeder.Result second = seeder.seed();

        assertThat(second).isEqualTo(new DemoSeeder.Result(0, 0));
        assertThat(first.tickets()).isIn(0, 60); // 0 if an earlier test in this JVM already seeded

        List<String> emails =
                DemoSeeder.everyone().stream().map(DemoData.Person::email).toList();
        Integer users = jdbc.sql("select count(*) from users where email in (:emails)")
                .param("emails", emails)
                .query(Integer.class)
                .single();
        assertThat(users).isEqualTo(9);

        List<Map<String, Object>> byStatus =
                jdbc.sql("""
                        select status, count(*) as n from tickets t join users u on u.id = t.requester_id
                        where u.email like '%@clinic.test' and u.email in (:emails) group by status""").param("emails", emails).query().listOfRows();
        assertThat(byStatus)
                .extracting(row -> row.get("status"))
                .containsExactlyInAnyOrder("NEW", "OPEN", "PENDING", "RESOLVED", "CLOSED", "CANCELLED");
        assertThat(byStatus.stream()
                        .mapToLong(row -> ((Number) row.get("n")).longValue())
                        .sum())
                .isEqualTo(60);

        Integer queues =
                jdbc.sql("""
                        select count(distinct queue_id) from tickets t join users u on u.id = t.requester_id where u.email in (:emails)""").param("emails", emails).query(Integer.class).single();
        assertThat(queues).isEqualTo(4);
        Integer unassignedButWorked =
                jdbc.sql("""
                        select count(*) from tickets t join users u on u.id = t.requester_id
                        where u.email in (:emails) and t.status in ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED') and t.assignee_id is null""").param("emails", emails).query(Integer.class).single();
        assertThat(unassignedButWorked).isZero();

        Integer closedWithoutFullHistory =
                jdbc.sql("""
                        select count(*) from tickets t join users u on u.id = t.requester_id
                        where u.email in (:emails) and t.status = 'CLOSED'
                          and (select count(*) from audit_events a where a.ticket_id = t.id) < 5""").param("emails", emails).query(Integer.class).single();
        assertThat(closedWithoutFullHistory)
                .as("created, assigned, opened, resolved, closed")
                .isZero();
    }

    @Test
    void everyDemoTicketHasClocksAndSomeAreAtRiskOrBreachedOnDayOne() {
        new DemoSeeder(jdbc, passwords, transactions, clock, timers, policies, calendars).seed();
        List<String> emails =
                DemoSeeder.everyone().stream().map(DemoData.Person::email).toList();
        List<UUID> tickets =
                jdbc.sql("""
                        select t.id from tickets t join users u on u.id = t.requester_id where u.email in (:emails)""").param("emails", emails).query(UUID.class).list();
        assertThat(tickets).hasSize(60);
        Map<SlaStatus, Long> resolutionClocks = tickets.stream()
                .flatMap(id -> timers.timersOf(id).stream())
                .filter(t -> t.kind().name().equals("RESOLUTION"))
                .collect(Collectors.groupingBy(TimerView::status, Collectors.counting()));
        assertThat(resolutionClocks.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(60);
        assertThat(resolutionClocks)
                .containsKeys(
                        SlaStatus.AT_RISK, SlaStatus.BREACHED, SlaStatus.MET, SlaStatus.PAUSED, SlaStatus.CANCELLED);
        // The three planted tickets, plus any older open ticket whose budget the time of day has eaten into.
        assertThat(resolutionClocks.get(SlaStatus.AT_RISK)).isGreaterThanOrEqualTo(3);
        assertThat(resolutionClocks.get(SlaStatus.CANCELLED)).isEqualTo(3);
        assertThat(resolutionClocks.get(SlaStatus.PAUSED)).isEqualTo(8);
    }

    @Test
    void theDemoAdminCanLogInWithTheDocumentedPassword() {
        new DemoSeeder(jdbc, passwords, transactions, clock, timers, policies, calendars).seed();
        jdbc.sql("update users set active = true where email = 'admin@clinic.test'")
                .update();
        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@clinic.test\",\"password\":\"Demo-Password-2026\"}"))
                .hasStatusOk();
    }
}
