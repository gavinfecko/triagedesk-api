package dev.gavinfecko.triagedesk.common.docs;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document: metadata, security schemes, the shared {@code ProblemDetail} schema and the
 * error responses every operation can return. Exported to {@code docs/openapi.yaml} by
 * {@code OpenApiSpecTest} and diffed in CI.
 */
@Configuration
public class OpenApiConfig {

    static final String PROBLEM_SCHEMA = "ProblemDetail";
    static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    /** Status codes any endpoint may answer with, and what they mean here. */
    static final Map<String, String> COMMON_ERRORS = new LinkedHashMap<>();

    static {
        COMMON_ERRORS.put("400", "Validation failed or the request is malformed");
        COMMON_ERRORS.put("401", "Authentication required");
        COMMON_ERRORS.put("403", "The caller's role does not allow this");
        COMMON_ERRORS.put("404", "The resource does not exist or is not visible to the caller");
        COMMON_ERRORS.put("409", "A business rule refused the request (see the type slug)");
        COMMON_ERRORS.put("500", "Unexpected error; quote the correlation id");
    }

    @Bean
    OpenAPI triageDeskOpenApi(@Value("${triagedesk.version:dev}") String version) {
        return new OpenAPI()
                .servers(List.of(new Server().url("/").description("This deployment")))
                .info(new Info()
                        .title("TriageDesk API")
                        .version(version)
                        .description("Help desk ticketing for a small IT team: tickets, queues, SLA timers that count "
                                + "business hours, an audit trail and reporting. Errors are RFC 9457 Problem Details "
                                + "with a stable `type` per error and a `correlation_id` on every response.")
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/gavinfecko/triagedesk-api/blob/main/LICENSE")))
                .externalDocs(new ExternalDocumentation()
                        .description("Architecture, process and backlog")
                        .url("https://github.com/gavinfecko/triagedesk-api/tree/main/docs"))
                .components(new Components()
                        .addSecuritySchemes(
                                "basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic"))
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    /**
     * Documents schemas in snake_case to match the wire format. swagger-core introspects models with
     * its own (Jackson 2) mapper, which does not see {@code spring.jackson.property-naming-strategy}.
     */
    @Bean
    ModelResolver snakeCaseModelResolver() {
        return new ModelResolver(Json.mapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
    }

    /**
     * Registers the {@code ProblemDetail} schema and adds the common error responses to every
     * operation that does not already declare them. Done in a customizer because springdoc rebuilds
     * the components from the scanned controllers before customizers run.
     */
    @Bean
    OpenApiCustomizer problemDetailsEverywhere() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemDetailSchema());
            if (openApi.getPaths() != null) {
                openApi.getPaths()
                        .values()
                        .forEach(path -> path.readOperations().forEach(this::addCommonErrors));
            }
        };
    }

    private void addCommonErrors(Operation operation) {
        COMMON_ERRORS.forEach((status, description) ->
                operation.getResponses().computeIfAbsent(status, s -> problemResponse(description)));
    }

    private static ApiResponse problemResponse(String description) {
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA);
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_MEDIA_TYPE, new MediaType().schema(ref)));
    }

    private static Schema<?> problemDetailSchema() {
        ObjectSchema fieldProblem = new ObjectSchema();
        fieldProblem.addProperty("field", new StringSchema().example("title"));
        fieldProblem.addProperty("message", new StringSchema().example("size must be between 5 and 120"));

        ObjectSchema schema = new ObjectSchema();
        schema.description(
                "RFC 9457 Problem Details. `type` is a stable slug under /problems/ that clients can switch on.");
        schema.addProperty("type", new StringSchema().example("/problems/validation"));
        schema.addProperty("title", new StringSchema().example("Validation failed"));
        schema.addProperty("status", new IntegerSchema().example(400));
        schema.addProperty("detail", new StringSchema().example("2 fields failed validation"));
        schema.addProperty("instance", new StringSchema().example("/api/v1/tickets"));
        schema.addProperty("correlation_id", new StringSchema().example("8b1f2c0e-4d3a-4f0e-9a6b-2c7d1e5f9a10"));
        schema.addProperty(
                "errors", new ArraySchema().items(fieldProblem).description("Present on validation problems only"));
        schema.setRequired(List.of("type", "title", "status", "correlation_id"));
        return schema;
    }
}
