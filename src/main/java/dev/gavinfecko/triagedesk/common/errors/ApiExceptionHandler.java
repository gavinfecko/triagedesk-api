package dev.gavinfecko.triagedesk.common.errors;

import dev.gavinfecko.triagedesk.common.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One error shape for the whole API: RFC 9457 Problem Details with a stable {@code type} per error
 * and the request's {@code correlation_id}. Extends Spring's handler so the framework's own errors
 * (unknown path, wrong method, unsupported media type…) come out the same way.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "/problems/";
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** One invalid field of a request body or parameter. */
    public record FieldProblem(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(jsonName(f.getField()), String.valueOf(f.getDefaultMessage())))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(
                                jsonName(parameterName(result)), String.valueOf(error.getDefaultMessage()))))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(validationProblem(errors));
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "arg" + result.getMethodParameter().getParameterIndex() : name;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        List<FieldProblem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldProblem(jsonName(lastNode(v)), v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(InvalidFieldException.class)
    ProblemDetail invalidField(InvalidFieldException ex) {
        return validationProblem(List.of(new FieldProblem(ex.field(), ex.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex) {
        return problem(ex.status(), ex.slug(), ex.title(), ex.getMessage());
    }

    @ExceptionHandler(DomainRuleException.class)
    ProblemDetail domainRule(DomainRuleException ex) {
        return problem(HttpStatus.CONFLICT, ex.slug(), titleFor(ex.slug()), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated(AuthenticationException ex) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "unauthenticated",
                "Authentication required",
                "Provide a valid credential in the Authorization header");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "Your role does not allow this");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception, correlation_id={}", CorrelationIdFilter.current(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal",
                "Unexpected error",
                "Something went wrong on our side. Quote the correlation id when reporting it.");
    }

    /** Every body the base class builds (404, 405, 415, 406, 400…) gets the same type and correlation id. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            decorate(pd, slugFor(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    static ProblemDetail problem(HttpStatus status, String slug, String title, @Nullable String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        decorate(pd, slug);
        return pd;
    }

    private static ProblemDetail validationProblem(List<FieldProblem> errors) {
        ProblemDetail pd = problem(
                HttpStatus.BAD_REQUEST,
                "validation",
                "Validation failed",
                errors.size() + " field" + (errors.size() == 1 ? "" : "s") + " failed validation");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static void decorate(ProblemDetail pd, String slug) {
        if (pd.getType()
                == null) { // Framework 7 leaves the type unset (implied about:blank); a handler's own type wins
            pd.setType(URI.create(TYPE_PREFIX + slug));
        }
        pd.setProperty("correlation_id", CorrelationIdFilter.current());
    }

    private static String slugFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "bad-request";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 406 -> "not-acceptable";
            case 415 -> "unsupported-media-type";
            default -> "http-" + status.value();
        };
    }

    private static String titleFor(String slug) {
        String words = slug.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** Java property names are camelCase; the API's JSON is snake_case, so errors name fields the client's way. */
    static String jsonName(String javaName) {
        return javaName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = String.valueOf(violation.getPropertyPath());
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
