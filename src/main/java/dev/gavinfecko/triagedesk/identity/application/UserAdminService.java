package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.ApiException;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.common.web.PageResponse;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.RefreshTokenRepository;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only user management. The role rule lives here as well as on the URL, so no future
 * controller can expose these operations to the wrong role. History is never rewritten: a
 * deactivated user stays the requester of their tickets.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final AuditLog audit;
    private final Clock clock;

    public UserAdminService(
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

    public record NewUser(String email, String displayName, Role role, String temporaryPassword) {}

    public record UserChanges(
            @Nullable String displayName, @Nullable Role role) {}

    @Transactional(readOnly = true)
    public PageResponse<UserView> list(@Nullable Role role, @Nullable Boolean active, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        return PageResponse.of(users.search(role, active, PageResponse.request(page, size, sort)), UserView::of);
    }

    @Transactional
    public UserView create(NewUser request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw RegistrationService.emailTaken();
        }
        UserAccount user = users.saveAndFlush(UserAccount.createWithTemporaryPassword(
                email,
                request.displayName(),
                passwords.encode(request.temporaryPassword()),
                request.role(),
                clock.instant()));
        audit.record(
                AuditEvent.of("user.created").actor(actor()).user(user.id()).change("role", null, user.role()));
        return UserView.of(user);
    }

    @Transactional
    public UserView update(UUID id, UserChanges changes) {
        UserAccount user = find(id);
        Instant now = clock.instant();
        if (changes.displayName() != null && !changes.displayName().strip().equals(user.displayName())) {
            String before = user.displayName();
            user.rename(changes.displayName(), now);
            audit.record(AuditEvent.of("user.renamed")
                    .actor(actor())
                    .user(id)
                    .change("display_name", before, user.displayName()));
        }
        if (changes.role() != null && changes.role() != user.role()) {
            if (user.role() == Role.ADMIN && user.active()) {
                requireAnotherActiveAdmin(id);
            }
            Role before = user.role();
            user.changeRole(changes.role(), now);
            audit.record(
                    AuditEvent.of("user.role_changed").actor(actor()).user(id).change("role", before, changes.role()));
        }
        return UserView.of(user);
    }

    @Transactional
    public UserView deactivate(UUID id) {
        UserAccount user = find(id);
        if (!user.active()) {
            return UserView.of(user);
        }
        if (user.role() == Role.ADMIN) {
            requireAnotherActiveAdmin(id);
        }
        Instant now = clock.instant();
        user.deactivate(now);
        refreshTokens.revokeAllForUser(id, now);
        audit.record(AuditEvent.of("user.deactivated").actor(actor()).user(id).change("active", true, false));
        return UserView.of(user);
    }

    private void requireAnotherActiveAdmin(UUID leaving) {
        boolean another = users.lockActiveByRole(Role.ADMIN).stream()
                .anyMatch(admin -> !admin.id().equals(leaving));
        if (!another) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "last-admin",
                    "Last admin",
                    "This is the only active admin; make someone else an admin first");
        }
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private static UUID actor() {
        return CurrentUser.get().id();
    }
}
