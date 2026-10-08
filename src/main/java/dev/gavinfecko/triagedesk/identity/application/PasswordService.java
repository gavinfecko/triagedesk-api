package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.RefreshTokenRepository;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user changes their own password. The current one must be given; afterwards every other session of theirs ends,
 * so a stolen session cannot outlive the change, while the session used to make it carries on.
 */
@Service
public class PasswordService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final AuditLog audit;
    private final Clock clock;

    public PasswordService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwords,
            AuditLog audit,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void change(String currentPassword, String newPassword) {
        UUID me = CurrentUser.get().id();
        UserAccount user = users.findById(me).orElseThrow(() -> new NotFoundException("User", me));
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw AuthService.invalidCredentials();
        }
        if (passwords.matches(newPassword, user.passwordHash())) {
            throw new InvalidFieldException("new_password", "must differ from the current password");
        }
        Instant now = clock.instant();
        user.changePassword(passwords.encode(newPassword), now);
        refreshTokens.revokeOtherFamilies(me, CurrentUser.sessionFamily(), now);
        audit.record(AuditEvent.of("user.password_changed").actor(me).user(me));
    }
}
