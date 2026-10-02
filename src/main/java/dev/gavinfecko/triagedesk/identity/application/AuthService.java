package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.errors.ApiException;
import dev.gavinfecko.triagedesk.identity.application.TokenService.TokenPair;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login. Every failure looks the same from outside, in body and in time spent. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final MeterRegistry meters;
    private final String timingDecoyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens, MeterRegistry meters) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.meters = meters;
        this.timingDecoyHash = passwords.encode("timing-decoy-" + UUID.randomUUID());
    }

    @Transactional
    public TokenPair login(String email, String password) {
        Optional<UserAccount> found = users.findByEmail(UserAccount.normalizeEmail(email));
        // Hash even when the account does not exist, so response time does not reveal which emails are real.
        boolean matches =
                passwords.matches(password, found.map(UserAccount::passwordHash).orElse(timingDecoyHash));
        if (found.isEmpty() || !matches || !found.get().active()) {
            meters.counter("auth.login.failed", "reason", "bad_credentials").increment();
            throw invalidCredentials();
        }
        return tokens.issue(found.get(), UUID.randomUUID()).pair();
    }

    static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Invalid credentials",
                "The email or password is incorrect, or the account is not active");
    }
}
