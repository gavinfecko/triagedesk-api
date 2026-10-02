package dev.gavinfecko.triagedesk.ticket.domain;

/** How urgent a ticket is; drives its SLA targets (TD-40). */
public enum Priority {
    P1_CRITICAL,
    P2_HIGH,
    P3_MEDIUM,
    P4_LOW
}
