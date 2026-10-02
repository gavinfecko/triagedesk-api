package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.ApiException;
import dev.gavinfecko.triagedesk.identity.application.TokenService.TokenPair;
import dev.gavinfecko.triagedesk.identity.domain.RefreshToken;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.RefreshTokenRepository;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh and logout. Login failures look the same from outside, in body and in time spent.
 * Refresh tokens rotate on every use; presenting a rotated token again revokes its whole family.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuditLog audit;
    private final MeterRegistry meters;
    private final Clock clock;
    private final String timingDecoyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwords,
            TokenService tokens,
            AuditLog audit,
            MeterRegistry meters,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.meters = meters;
        this.clock = clock;
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

    /**
     * Exchanges a refresh token for a new pair in the same family. Refusals still commit, because
     * reuse detection must keep the family revoked even though the request fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens
                .findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(
                        () -> unauthorized("invalid-token", "Invalid token", "This refresh token is not recognised"));
        if (token.rotated()) {
            refreshTokens.revokeFamily(token.familyId(), now);
            audit.record(AuditEvent.of("auth.token_reuse_detected").user(token.userId()));
            meters.counter("auth.login.failed", "reason", "token_reuse").increment();
            throw unauthorized(
                    "token-reuse",
                    "Token reuse",
                    "This refresh token was already used; every session token in its family has been revoked");
        }
        if (token.revoked()) {
            throw unauthorized("token-revoked", "Token revoked", "This session has ended; log in again");
        }
        if (token.expiredAt(now)) {
            throw unauthorized("token-expired", "Token expired", "This refresh token has expired; log in again");
        }
        UserAccount user =
                users.findById(token.userId()).filter(UserAccount::active).orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(token.familyId(), now);
            throw invalidCredentials();
        }
        TokenService.Issued next = tokens.issue(user, token.familyId());
        token.replaceWith(next.stored().id(), now);
        return next.pair();
    }

    /** Ends the caller's session: every refresh token in its family stops working. */
    @Transactional
    public void logout(UUID family) {
        refreshTokens.revokeFamily(family, clock.instant());
    }

    private static ApiException unauthorized(String slug, String title, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, slug, title, detail);
    }

    static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Invalid credentials",
                "The email or password is incorrect, or the account is not active");
    }
}
