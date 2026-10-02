package dev.gavinfecko.triagedesk.common.audit;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One immutable fact for the audit trail. Built fluently:
 * {@code AuditEvent.of("user.role_changed").actor(adminId).user(id).change("role", "AGENT", "ADMIN")}.
 */
public record AuditEvent(
        String action,
        @Nullable UUID actorId,
        @Nullable UUID userId,
        @Nullable UUID ticketId,
        @Nullable String field,
        @Nullable Object before,
        @Nullable Object after) {

    public static AuditEvent of(String action) {
        return new AuditEvent(action, null, null, null, null, null, null);
    }

    public AuditEvent actor(@Nullable UUID actor) {
        return new AuditEvent(action, actor, userId, ticketId, field, before, after);
    }

    public AuditEvent user(UUID user) {
        return new AuditEvent(action, actorId, user, ticketId, field, before, after);
    }

    public AuditEvent ticket(UUID ticket) {
        return new AuditEvent(action, actorId, userId, ticket, field, before, after);
    }

    public AuditEvent change(String changedField, @Nullable Object oldValue, @Nullable Object newValue) {
        return new AuditEvent(action, actorId, userId, ticketId, changedField, oldValue, newValue);
    }
}
