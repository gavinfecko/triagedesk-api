package dev.gavinfecko.triagedesk.common.errors;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.SecurityConfig;
import dev.gavinfecko.triagedesk.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ProbeController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, CorrelationIdFilter.class})
class ApiExceptionHandlerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    @WithMockUser
    void invalidBodyIsA400ValidationProblemListingEachField() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"ab\",\"count\":0}")
                .exchange();
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation failed");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("title", "count");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.correlation_id")
                .asString()
                .isNotBlank();
    }

    @Test
    @WithMockUser
    void constraintViolationFromAServiceIsTheSameValidationProblem() {
        MvcTestResult result = mvc.get().uri("/api/v1/probe/service-violation").exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("2 fields failed validation");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("title", "count");
    }

    @Test
    @WithMockUser
    void validBodyPassesThrough() {
        assertThat(mvc.post()
                        .uri("/api/v1/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Printer jam\",\"count\":2}"))
                .hasStatusOk();
    }

    @Test
    @WithMockUser
    void missingResourceIsA404Problem() {
        MvcTestResult result = mvc.get().uri("/api/v1/probe/not-found").exchange();
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Ticket HD-000001 was not found");
    }

    @Test
    @WithMockUser
    void domainRuleIsA409WithTheRulesOwnType() {
        MvcTestResult result = mvc.get().uri("/api/v1/probe/conflict").exchange();
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/ticket-state-conflict");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Ticket state conflict");
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("CLOSED");
    }

    @Test
    @WithMockUser
    void unexpectedExceptionIsA500ThatLeaksNothing() {
        MvcTestResult result = mvc.get().uri("/api/v1/probe/boom").exchange();
        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/internal");
        assertThat(result).bodyText().doesNotContain("secret internal detail").doesNotContain("IllegalStateException");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.correlation_id")
                .asString()
                .isNotBlank();
    }

    @Test
    @WithMockUser
    void malformedJsonIsA400BadRequestProblem() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/bad-request");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.correlation_id")
                .asString()
                .isNotBlank();
    }

    @Test
    @WithMockUser
    void unsupportedMediaTypeIsA415Problem() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/probe/validate")
                .contentType(MediaType.TEXT_PLAIN)
                .content("title=Printer")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/unsupported-media-type");
    }

    @Test
    @WithMockUser
    void unacceptableAcceptHeaderIsA406Problem() {
        // The JSON-only endpoint: a String endpoint would be written under any Accept type.
        MvcTestResult result = mvc.post()
                .uri("/api/v1/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_XML)
                .content("{\"title\":\"Printer jam\",\"count\":2}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_ACCEPTABLE);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/not-acceptable");
    }

    @Test
    @WithMockUser
    void unknownPathIsA404ProblemToo() {
        MvcTestResult result = mvc.get().uri("/api/v1/nothing-here").exchange();
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/not-found");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.correlation_id")
                .asString()
                .isNotBlank();
    }

    @Test
    @WithMockUser
    void wrongMethodIsA405Problem() {
        MvcTestResult result = mvc.delete().uri("/api/v1/probe/ok").exchange();
        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/method-not-allowed");
    }

    @Test
    void unauthenticatedIsA401Problem() {
        MvcTestResult result = mvc.get().uri("/api/v1/probe/ok").exchange();
        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/unauthenticated");
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    @WithMockUser
    void callerSuppliedCorrelationIdIsEchoedInHeaderAndBody() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/probe/not-found")
                .header(CorrelationIdFilter.HEADER, "front-desk-42")
                .exchange();
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo("front-desk-42");
        assertThat(result).bodyJson().extractingPath("$.correlation_id").isEqualTo("front-desk-42");
    }

    @Test
    @WithMockUser
    void unsafeCorrelationIdIsReplaced() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/probe/ok")
                .header(CorrelationIdFilter.HEADER, "<script>alert(1)</script>")
                .exchange();
        String echoed = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(echoed).isNotNull().doesNotContain("<").hasSize(36);
    }
}
