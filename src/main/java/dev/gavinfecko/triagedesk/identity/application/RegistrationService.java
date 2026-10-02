package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.ApiException;
import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service sign-up. Everyone who registers is a requester; agents and admins are created by admins. */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditLog audit;
    private final Clock clock;

    public RegistrationService(UserRepository users, PasswordEncoder passwords, AuditLog audit, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    public record Registration(String email, String displayName, String password) {}

    @Transactional
    public UserView register(Registration request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        UserAccount user = UserAccount.create(
                email, request.displayName(), passwords.encode(request.password()), Role.REQUESTER, clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException raceWithAnotherRegistration) {
            throw emailTaken();
        }
        audit.record(AuditEvent.of("user.registered").actor(user.id()).user(user.id()));
        return UserView.of(user);
    }

    /** Same answer whether the existing account is active or not, so the endpoint reveals nothing more. */
    static ApiException emailTaken() {
        return new ApiException(
                HttpStatus.CONFLICT, "email-taken", "Email taken", "An account with this email already exists");
    }
}
