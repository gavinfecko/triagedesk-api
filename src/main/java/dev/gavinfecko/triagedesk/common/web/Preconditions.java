package dev.gavinfecko.triagedesk.common.web;

import dev.gavinfecko.triagedesk.common.errors.ApiException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Optimistic concurrency over HTTP (ARCHITECTURE.md §7): reads carry {@code ETag: "<version>"}, and
 * writes must send it back as {@code If-Match}. A missing header is 428, a stale one is 412.
 */
public final class Preconditions {

    private Preconditions() {}

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** The version the caller last saw, or a 428 if they did not say. */
    public static long expectedVersion(@Nullable String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "precondition-required",
                    "Precondition required",
                    "Send the ETag you last read in an If-Match header, so an edit cannot overwrite someone else's");
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notAVersion) {
            throw stale();
        }
    }

    public static ApiException stale() {
        return new ApiException(
                HttpStatus.PRECONDITION_FAILED,
                "precondition-failed",
                "Precondition failed",
                "The ticket changed since you read it; reload it and apply your edit again");
    }
}
