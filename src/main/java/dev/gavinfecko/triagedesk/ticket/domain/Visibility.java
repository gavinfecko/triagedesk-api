package dev.gavinfecko.triagedesk.ticket.domain;

/** Who may read a comment. */
public enum Visibility {
    /** The requester and staff. */
    PUBLIC,
    /** Agents and admins only. */
    INTERNAL
}
