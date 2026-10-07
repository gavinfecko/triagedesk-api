package dev.gavinfecko.triagedesk.ticket.domain;

import dev.gavinfecko.triagedesk.common.security.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where a ticket is in its life, and the only legal ways to move it (ARCHITECTURE.md §4). The table
 * is data, so a test can walk every (from, to, role) triple and the API can never disagree with it.
 */
public enum TicketStatus {
    NEW,
    OPEN,
    PENDING,
    RESOLVED,
    CLOSED,
    CANCELLED;

    /** One row of the transition table. */
    public record Transition(TicketStatus from, TicketStatus to, Set<Role> allowed, boolean commentRequired) {
        public boolean allows(Role role) {
            return allowed.contains(role);
        }
    }

    private static final Set<Role> STAFF = EnumSet.of(Role.AGENT, Role.ADMIN);
    private static final Set<Role> ANYONE = EnumSet.allOf(Role.class);
    private static final Set<Role> ADMIN = EnumSet.of(Role.ADMIN);
    private static final Set<Role> REQUESTER_OR_ADMIN = EnumSet.of(Role.REQUESTER, Role.ADMIN);

    private static final Map<TicketStatus, Map<TicketStatus, Transition>> TABLE = new EnumMap<>(TicketStatus.class);

    static {
        // From NEW: staff acknowledge or assign (TD-24 assigns); the requester may still cancel; admins may cancel.
        allow(NEW, OPEN, STAFF, false);
        allow(NEW, CANCELLED, REQUESTER_OR_ADMIN, false);
        // From OPEN: wait on the requester (say why), resolve (say what was done), or an admin cancels.
        allow(OPEN, PENDING, STAFF, true);
        allow(OPEN, RESOLVED, STAFF, true);
        allow(OPEN, CANCELLED, ADMIN, false);
        // From PENDING: the requester's reply (TD-30) or anyone reopens; staff may resolve; an admin cancels.
        allow(PENDING, OPEN, ANYONE, false);
        allow(PENDING, RESOLVED, STAFF, true);
        allow(PENDING, CANCELLED, ADMIN, false);
        // From RESOLVED: the requester (or an admin, or the scheduler) closes; anyone may reopen (window: TD-27).
        allow(RESOLVED, CLOSED, REQUESTER_OR_ADMIN, false);
        allow(RESOLVED, OPEN, ANYONE, false);
        // CLOSED and CANCELLED are terminal.
    }

    private static void allow(TicketStatus from, TicketStatus to, Set<Role> roles, boolean commentRequired) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(TicketStatus.class))
                .put(to, new Transition(from, to, roles, commentRequired));
    }

    /** The table row for this move, if the move exists at all (for anyone). */
    public Optional<Transition> transitionTo(TicketStatus to) {
        return Optional.ofNullable(TABLE.getOrDefault(this, Map.of()).get(to));
    }

    public boolean canTransition(TicketStatus to, Role role) {
        return transitionTo(to).map(t -> t.allows(role)).orElse(false);
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
