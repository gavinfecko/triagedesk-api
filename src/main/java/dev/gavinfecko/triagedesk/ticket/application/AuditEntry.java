package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One fact from a ticket's audit trail. {@code actor} is null when the system did it (schedulers).
 * {@code before} and {@code after} are whatever the change recorded: a status, an id, a title…
 */
public record AuditEntry(
        long id,
        String action,
        @Nullable Person actor,
        @Nullable String field,
        @Nullable JsonNode before,
        @Nullable JsonNode after,
        Instant createdAt) {}
