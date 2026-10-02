package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** Published once per new ticket. SLA timers (TD-42) and notifications (TD-50) subscribe to it. */
public record TicketCreated(UUID ticketId, String key, Priority priority, UUID requesterId, Instant createdAt) {}
