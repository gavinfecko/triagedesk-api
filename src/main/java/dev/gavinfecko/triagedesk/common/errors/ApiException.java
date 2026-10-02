package dev.gavinfecko.triagedesk.common.errors;

import org.springframework.http.HttpStatus;

/**
 * A refusal with a specific HTTP status and a stable Problem Details slug, for cases that are not a
 * plain 404 or a 409 business rule (for example 401 invalid credentials or 429 throttling).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;
    private final String title;

    public ApiException(HttpStatus status, String slug, String title, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }
}
