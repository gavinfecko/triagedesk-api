package dev.gavinfecko.triagedesk.common.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void customizerCopesWithAnEmptyDocument() {
        OpenAPI empty = new OpenAPI();
        config.problemDetailsEverywhere().customise(empty);
        assertThat(empty.getComponents().getSchemas()).containsKey(OpenApiConfig.PROBLEM_SCHEMA);
        assertThat(empty.getPaths()).isNull();
    }

    @Test
    void customizerAddsMissingErrorResponsesAndKeepsDeclaredOnes() {
        ApiResponses responses =
                new ApiResponses().addApiResponse("404", new ApiResponse().description("Ticket not found"));
        Operation get = new Operation().responses(responses);
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/tickets/{key}", new PathItem().get(get)));
        config.problemDetailsEverywhere().customise(api);
        assertThat(get.getResponses()).containsKeys("400", "401", "403", "404", "409", "500");
        assertThat(get.getResponses().get("404").getDescription()).isEqualTo("Ticket not found");
        assertThat(get.getResponses().get("409").getContent()).containsKey(OpenApiConfig.PROBLEM_MEDIA_TYPE);
    }
}
