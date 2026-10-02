package dev.gavinfecko.triagedesk.common.errors;

/**
 * A request field that is well-formed but refers to something that does not exist or is not usable
 * (an unknown category id, an inactive requester). Reported as a normal validation problem.
 */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
