package dev.gavinfecko.triagedesk.identity.infra;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    @Query(
            "select u from UserAccount u where (:role is null or u.role = :role) and (:active is null or u.active = :active)")
    Page<UserAccount> search(@Nullable Role role, @Nullable Boolean active, Pageable pageable);

    /** Locks the active admins so two concurrent demotions cannot both pass the last-admin check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.role = :role and u.active = true")
    List<UserAccount> lockActiveByRole(Role role);
}
