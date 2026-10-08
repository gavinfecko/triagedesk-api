package dev.gavinfecko.triagedesk.identity.domain;

import dev.gavinfecko.triagedesk.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A person who can log in. Emails are stored lower-case; the database checks it. */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {}

    private UserAccount(UUID id, String email, String displayName, String passwordHash, Role role, Instant now) {
        this.id = id;
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount create(String email, String displayName, String passwordHash, Role role, Instant now) {
        return new UserAccount(UUID.randomUUID(), email, displayName, passwordHash, role, now);
    }

    /** An account an admin creates: any role, with a temporary password the user should replace. */
    public static UserAccount createWithTemporaryPassword(
            String email, String displayName, String passwordHash, Role role, Instant now) {
        UserAccount user = new UserAccount(UUID.randomUUID(), email, displayName, passwordHash, role, now);
        user.mustChangePassword = true;
        return user;
    }

    public void rename(String newName, Instant now) {
        this.displayName = newName.strip();
        this.updatedAt = now;
    }

    public void changeRole(Role newRole, Instant now) {
        this.role = newRole;
        this.updatedAt = now;
    }

    /** A new password chosen by the user: any temporary-password requirement is satisfied. */
    public void changePassword(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public boolean active() {
        return active;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
