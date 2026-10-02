package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;

/** A user as the API shows it. Never carries the password hash. */
public record UserView(UUID id, String email, String displayName, Role role, boolean active, Instant createdAt) {

    public static UserView of(UserAccount user) {
        return new UserView(user.id(), user.email(), user.displayName(), user.role(), user.active(), user.createdAt());
    }
}
