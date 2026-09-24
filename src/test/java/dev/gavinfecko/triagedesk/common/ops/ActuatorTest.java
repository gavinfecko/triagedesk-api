package dev.gavinfecko.triagedesk.common.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Health is public for the platform; everything else under /actuator is for admins. Boot disables
 * metrics exporters inside tests, so {@code @AutoConfigureMetrics} turns the Prometheus endpoint on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
class ActuatorTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthGroupsArePublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatusOk();
    }

    @Test
    void infoMetricsAndPrometheusRequireAuthentication() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/prometheus")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void nonAdminsAreForbidden() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminsSeeBuildInfoMetricsAndPrometheus() {
        MvcTestResult info = mvc.get().uri("/actuator/info").exchange();
        assertThat(info).hasStatusOk();
        assertThat(info).bodyJson().extractingPath("$.build.artifact").isEqualTo("triagedesk-api");
        assertThat(info).bodyJson().extractingPath("$.build.version").asString().isNotBlank();
        assertThat(mvc.get().uri("/actuator/metrics/jvm.memory.used")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/prometheus"))
                .hasStatusOk()
                .bodyText()
                .contains("jvm_memory_used_bytes");
    }
}
