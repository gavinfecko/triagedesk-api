package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Walks every (from, to, role) triple so the table in TicketStatus cannot drift from ARCHITECTURE.md §4. */
class TicketStatusTest {

    record Allowed(TicketStatus from, TicketStatus to, Role role) {}

    static Set<Allowed> expected() {
        Set<Allowed> all = new java.util.HashSet<>();
        for (Role staff : Set.of(Role.AGENT, Role.ADMIN)) {
            all.add(new Allowed(TicketStatus.NEW, TicketStatus.OPEN, staff));
            all.add(new Allowed(TicketStatus.OPEN, TicketStatus.PENDING, staff));
            all.add(new Allowed(TicketStatus.OPEN, TicketStatus.RESOLVED, staff));
            all.add(new Allowed(TicketStatus.PENDING, TicketStatus.RESOLVED, staff));
        }
        for (Role anyone : Role.values()) {
            all.add(new Allowed(TicketStatus.PENDING, TicketStatus.OPEN, anyone));
            all.add(new Allowed(TicketStatus.RESOLVED, TicketStatus.OPEN, anyone));
        }
        for (Role r : Set.of(Role.REQUESTER, Role.ADMIN)) {
            all.add(new Allowed(TicketStatus.NEW, TicketStatus.CANCELLED, r));
            all.add(new Allowed(TicketStatus.RESOLVED, TicketStatus.CLOSED, r));
        }
        all.add(new Allowed(TicketStatus.OPEN, TicketStatus.CANCELLED, Role.ADMIN));
        all.add(new Allowed(TicketStatus.PENDING, TicketStatus.CANCELLED, Role.ADMIN));
        return all;
    }

    @Test
    void everyTripleMatchesTheArchitectureTable() {
        Set<Allowed> expected = expected();
        int checked = 0;
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                for (Role role : Role.values()) {
                    boolean allowed = from.canTransition(to, role);
                    assertThat(allowed)
                            .as("%s -> %s as %s", from, to, role)
                            .isEqualTo(expected.contains(new Allowed(from, to, role)));
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(6 * 6 * 3);
        assertThat(expected).hasSize(20); // 8 staff + 6 anyone + 4 requester-or-admin + 2 admin-only
    }

    @Test
    void commentsAreRequiredExactlyForPendingAndResolved() {
        assertThat(TicketStatus.OPEN.transitionTo(TicketStatus.PENDING))
                .get()
                .extracting(t -> t.commentRequired())
                .isEqualTo(true);
        assertThat(TicketStatus.OPEN.transitionTo(TicketStatus.RESOLVED))
                .get()
                .extracting(t -> t.commentRequired())
                .isEqualTo(true);
        assertThat(TicketStatus.PENDING.transitionTo(TicketStatus.RESOLVED))
                .get()
                .extracting(t -> t.commentRequired())
                .isEqualTo(true);
        assertThat(TicketStatus.NEW.transitionTo(TicketStatus.OPEN))
                .get()
                .extracting(t -> t.commentRequired())
                .isEqualTo(false);
        assertThat(TicketStatus.RESOLVED.transitionTo(TicketStatus.CLOSED))
                .get()
                .extracting(t -> t.commentRequired())
                .isEqualTo(false);
    }

    @Test
    void terminalStatesHaveNoExits() {
        for (TicketStatus terminal : Set.of(TicketStatus.CLOSED, TicketStatus.CANCELLED)) {
            assertThat(terminal.isTerminal()).isTrue();
            for (TicketStatus to : TicketStatus.values()) {
                assertThat(terminal.transitionTo(to))
                        .as("%s -> %s", terminal, to)
                        .isEmpty();
            }
        }
        assertThat(TicketStatus.OPEN.isTerminal()).isFalse();
        assertThat(TicketStatus.OPEN.transitionTo(TicketStatus.OPEN)).isEmpty();
    }
}
