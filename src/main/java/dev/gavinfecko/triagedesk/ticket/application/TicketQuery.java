package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;

/**
 * A ticket list request (ARCHITECTURE.md §7): filters combine with AND, values inside one filter
 * with OR, and {@code sort} accepts only the fields listed here. {@code assignee} is {@code "me"},
 * {@code "unassigned"} or a user id; {@code tag} is one tag name. Full-text ({@code q}) and SLA
 * status arrive with TD-33 and TD-44.
 */
public record TicketQuery(
        List<TicketStatus> status,
        List<Priority> priority,
        @Nullable UUID queueId,
        @Nullable UUID categoryId,
        @Nullable UUID requesterId,
        @Nullable String assignee,
        @Nullable String tag,
        List<String> sort,
        int page,
        int size) {

    /** API sort names → entity properties. The order of the map is irrelevant; the allow-list is. */
    static final Map<String, String> SORTABLE = Map.of(
            "created_at", "createdAt",
            "updated_at", "updatedAt",
            "priority", "priority",
            "status", "status",
            "key", "key");

    public static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    /** Parses {@code field,asc|desc} items; an unknown field is a validation problem naming {@code sort}. */
    public Sort toSort() {
        if (sort == null || sort.isEmpty()) {
            return DEFAULT_SORT;
        }
        List<Sort.Order> orders = sort.stream().map(TicketQuery::order).toList();
        return Sort.by(orders).and(Sort.by(Sort.Order.asc("id")));
    }

    private static Sort.Order order(String item) {
        String[] parts = item.split(",", 2);
        String property = SORTABLE.get(parts[0].strip());
        if (property == null) {
            throw new InvalidFieldException(
                    "sort", "unknown sort field '" + parts[0].strip() + "'; use one of " + SORTABLE.keySet());
        }
        String direction = parts.length > 1 ? parts[1].strip().toLowerCase(java.util.Locale.ROOT) : "asc";
        return switch (direction) {
            case "asc" -> Sort.Order.asc(property);
            case "desc" -> Sort.Order.desc(property);
            default ->
                throw new InvalidFieldException("sort", "direction must be asc or desc, not '" + direction + "'");
        };
    }
}
