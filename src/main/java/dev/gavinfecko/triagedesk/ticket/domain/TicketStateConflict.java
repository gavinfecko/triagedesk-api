package dev.gavinfecko.triagedesk.ticket.domain;

import dev.gavinfecko.triagedesk.common.errors.DomainRuleException;
import dev.gavinfecko.triagedesk.common.security.Role;

/** The requested move is not in the transition table for this status and role. */
public class TicketStateConflict extends DomainRuleException {

    public static final String SLUG = "ticket-state-conflict";

    public TicketStateConflict(String key, TicketStatus current, TicketStatus requested, Role role) {
        super(
                SLUG,
                "Ticket " + key + " is " + current + "; "
                        + (current.isTerminal()
                                ? "it is closed to further changes"
                                : "a " + role.name().toLowerCase() + " cannot move it to " + requested));
    }

    public TicketStateConflict(String key, TicketStatus current, String reason) {
        super(SLUG, "Ticket " + key + " is " + current + "; " + reason);
    }
}
