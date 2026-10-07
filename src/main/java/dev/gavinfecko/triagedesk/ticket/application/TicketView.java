package dev.gavinfecko.triagedesk.ticket.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A ticket as the API shows it, with names resolved. {@code warnings} appears only when there are any. */
public record TicketView(
        UUID id,
        String key,
        String title,
        String description,
        TicketStatus status,
        Priority priority,
        Ref category,
        Ref queue,
        Person requester,
        @Nullable Person assignee,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant firstRespondedAt,
        @Nullable Instant resolvedAt,
        @Nullable Instant closedAt,
        int reopenCount,
        long version,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> warnings) {

    public record Ref(UUID id, String name) {}

    public record Person(UUID id, String displayName) {}
}
