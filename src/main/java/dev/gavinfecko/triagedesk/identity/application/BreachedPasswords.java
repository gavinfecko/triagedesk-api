package dev.gavinfecko.triagedesk.identity.application;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A small list of passwords that appear in breach corpora and still pass a length rule. Compared
 * case-insensitively. A real deployment would call a k-anonymity service; this keeps the demo offline.
 */
public final class BreachedPasswords {

    private static final Set<String> LIST = load();

    private BreachedPasswords() {}

    public static boolean contains(String password) {
        return LIST.contains(password.toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        try (InputStream in = BreachedPasswords.class.getResourceAsStream("/security/breached-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("security/breached-passwords.txt is missing from the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bean Validation hook for {@link NotBreached}. Null is left to {@code @NotBlank}. */
    public static class Validator implements ConstraintValidator<NotBreached, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || !contains(value);
        }
    }
}
