package dev.gavinfecko.triagedesk.ticket.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What the ticket list shows about SLA clocks. The SLA module implements it, so the ticket module never depends on
 * the SLA module (the dependency runs the other way).
 */
public interface TicketSlaLookup {

    Map<UUID, TicketSummary.Sla> forTickets(Collection<UUID> ticketIds);
}
