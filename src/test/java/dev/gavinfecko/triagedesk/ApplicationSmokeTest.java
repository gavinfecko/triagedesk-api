package dev.gavinfecko.triagedesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationSmokeTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void contextLoadsAgainstRealPostgres() {
        // The context itself is the assertion: Flyway ran, JPA validated, security wired.
    }

    @Test
    void healthIsPublicAndUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void livenessAndReadinessProbesAreExposed() {
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatusOk();
    }

    @Test
    void everythingElseRequiresAuthentication() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
