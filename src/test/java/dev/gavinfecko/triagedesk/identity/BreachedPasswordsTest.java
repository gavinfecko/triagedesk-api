package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.identity.application.BreachedPasswords;
import org.junit.jupiter.api.Test;

class BreachedPasswordsTest {

    @Test
    void matchesCaseInsensitively() {
        assertThat(BreachedPasswords.contains("QWERTY123456")).isTrue();
        assertThat(BreachedPasswords.contains("correct horse battery")).isFalse();
    }

    @Test
    void validatorLeavesNullToNotBlank() {
        assertThat(new BreachedPasswords.Validator().isValid(null, null)).isTrue();
        assertThat(new BreachedPasswords.Validator().isValid("password1234", null))
                .isFalse();
    }
}
