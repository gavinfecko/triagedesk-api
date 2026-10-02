package dev.gavinfecko.triagedesk.ticket.domain;

/** Where a ticket is in its life (ARCHITECTURE.md §4). The transition table arrives with TD-23. */
public enum TicketStatus {
    NEW,
    OPEN,
    PENDING,
    RESOLVED,
    CLOSED,
    CANCELLED
}
