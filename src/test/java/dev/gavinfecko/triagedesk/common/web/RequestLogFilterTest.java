package dev.gavinfecko.triagedesk.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    @WithMockUser(username = "agent.ana", roles = "ADMIN")
    void oneAccessLineWithMethodPathStatusDurationAndUser(CapturedOutput output) {
        mvc.get().uri("/actuator/info").exchange();
        assertThat(output.getOut().lines().filter(l -> l.contains("GET /actuator/info -> 200")))
                .hasSize(1)
                .first().asString().contains(" ms)").contains("user_id=agent.ana");
    }

    @Test
    void rejectedRequestsAreLoggedTooWithoutAUser(CapturedOutput output) {
        mvc.get().uri("/api/v1/nothing").exchange();
        assertThat(output.getOut()).contains("GET /api/v1/nothing -> 401").doesNotContain("user_id=");
    }

    @Test
    void healthProbesAreNotLogged(CapturedOutput output) {
        mvc.get().uri("/actuator/health/liveness").exchange();
        assertThat(output.getOut()).doesNotContain("/actuator/health/liveness ->");
    }
}
