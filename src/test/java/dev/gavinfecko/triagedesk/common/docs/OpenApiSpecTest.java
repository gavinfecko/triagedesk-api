package dev.gavinfecko.triagedesk.common.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Proves the documentation is served and keeps {@code docs/openapi.yaml} in sync with the code:
 * every run rewrites the file, and CI fails if the result differs from what is committed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "triagedesk.docs.public=true",
            "springdoc.paths-to-exclude=/api/v1/probe/**" // test-only endpoints must not enter the contract
        })
class OpenApiSpecTest {

    static final Path SPEC = Path.of("docs", "openapi.yaml");

    @Autowired
    MockMvcTester mvc;

    @Test
    void specIsServedAsYamlAndExportedToDocs() throws IOException {
        MvcTestResult result = mvc.get().uri("/v3/api-docs.yaml").exchange();
        assertThat(result).hasStatusOk();
        String yaml = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml)
                .contains("title: TriageDesk API")
                .contains("ProblemDetail")
                .doesNotContain("/probe/");
        Files.createDirectories(SPEC.getParent());
        Files.writeString(SPEC, yaml.endsWith("\n") ? yaml : yaml + "\n", StandardCharsets.UTF_8);
    }

    @Test
    void problemDetailSchemaAndSecuritySchemesAreDeclared() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.components.schemas.ProblemDetail.required")
                .asArray()
                .contains("type", "status", "correlation_id");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer");
    }

    @Test
    void swaggerUiRendersWhenDocsArePublic() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }
}
