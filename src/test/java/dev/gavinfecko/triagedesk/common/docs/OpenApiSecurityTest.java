package dev.gavinfecko.triagedesk.common.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** With the default (prod-like) setting, the docs are for admins only. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSecurityTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void anonymousCallersGet401() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void nonAdminsGet403() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminsCanReadTheDocs() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }
}
