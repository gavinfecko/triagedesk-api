package dev.gavinfecko.triagedesk.common.errors;

/** A resource the caller named does not exist, or is not visible to them (same answer on purpose). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object identifier) {
        super(what + " " + identifier + " was not found");
    }
}
