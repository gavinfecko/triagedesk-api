package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other modules may ask the identity module: who someone is, and whether they can be used. */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(UserAccount::id, UserAccount::displayName));
    }

    /** Where to reach someone: an active user's email and name. Deactivated users get no mail. */
    public record Contact(UUID id, String email, String displayName) {}

    public Optional<Contact> contact(UUID id) {
        return users.findById(id).filter(UserAccount::active).map(UserDirectory::contactOf);
    }

    public List<Contact> activeAdmins() {
        return users.findByRoleAndActiveTrue(Role.ADMIN).stream()
                .map(UserDirectory::contactOf)
                .toList();
    }

    private static Contact contactOf(UserAccount u) {
        return new Contact(u.id(), u.email(), u.displayName());
    }

    public boolean isActive(UUID id) {
        return users.findById(id).map(UserAccount::active).orElse(false);
    }

    /** Active and able to work tickets (agent or admin). */
    public boolean isActiveStaff(UUID id) {
        return users.findById(id)
                .filter(UserAccount::active)
                .map(u -> u.role() == Role.AGENT || u.role() == Role.ADMIN)
                .orElse(false);
    }
}
