package dev.gavinfecko.triagedesk.common.errors;

/**
 * A business rule refused the request. Each rule gets its own subclass with a stable slug, which
 * becomes the Problem Details {@code type} ({@code /problems/<slug>}) clients can switch on. The
 * HTTP status is always 409.
 */
public abstract class DomainRuleException extends RuntimeException {

    private final String slug;

    protected DomainRuleException(String slug, String message) {
        super(message);
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
