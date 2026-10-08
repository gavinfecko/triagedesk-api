package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;

/**
 * A ticket list request (ARCHITECTURE.md §7): filters combine with AND, values inside one filter
 * with OR, and {@code sort} accepts only the fields listed here. {@code assignee} is {@code "me"},
 * {@code "unassigned"} or a user id; {@code tag} is one tag name; {@code q} searches title and description, ranked
 * by relevance unless a sort is given. {@code slaStatus} filters on the resolution clock; {@code sla_due} sorts by
 * when it is due and cannot be combined with other sort fields.
 */
public record TicketQuery(
        List<TicketStatus> status,
        List<Priority> priority,
        @Nullable UUID queueId,
        @Nullable UUID categoryId,
        @Nullable UUID requesterId,
        @Nullable String assignee,
        @Nullable String tag,
        List<String> slaStatus,
        @Nullable String q,
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

    static final Set<String> SLA_STATUSES = Set.of("on_track", "at_risk", "breached", "met", "paused", "cancelled");

    static final String SLA_DUE = "sla_due";

    public TicketQuery {
        slaStatus = slaStatus == null
                ? List.of()
                : slaStatus.stream()
                        .map(s -> s.strip().toLowerCase(Locale.ROOT))
                        .toList();
        for (String s : slaStatus) {
            if (!SLA_STATUSES.contains(s)) {
                throw new InvalidFieldException(
                        "sla_status", "unknown SLA status '" + s + "'; use one of " + new TreeSet<>(SLA_STATUSES));
            }
        }
    }

    /** The direction of an {@code sla_due} sort, or null when the list is not sorted by it. */
    public Sort.@Nullable Direction slaDueDirection() {
        if (!hasExplicitSort()
                || sort.stream().noneMatch(s -> s.split(",", 2)[0].strip().equals(SLA_DUE))) {
            return null;
        }
        if (sort.size() > 1) {
            throw new InvalidFieldException("sort", "sla_due cannot be combined with other sort fields");
        }
        String[] parts = sort.get(0).split(",", 2);
        String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
        return switch (direction) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default ->
                throw new InvalidFieldException("sort", "direction must be asc or desc, not '" + direction + "'");
        };
    }

    public static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    public boolean hasText() {
        return q != null && !q.isBlank();
    }

    public boolean hasExplicitSort() {
        return sort != null && !sort.isEmpty();
    }

    /**
     * Parses {@code field,asc|desc} items; an unknown field is a validation problem naming {@code sort}.
     * A text search without an explicit sort is ordered by relevance inside the query instead.
     */
    public Sort toSort() {
        if (!hasExplicitSort()) {
            return hasText() ? Sort.unsorted() : DEFAULT_SORT;
        }
        if (slaDueDirection() != null) {
            return Sort.unsorted(); // ordered inside the query by the SQL function
        }
        List<Sort.Order> orders = sort.stream().map(TicketQuery::order).toList();
        return Sort.by(orders).and(Sort.by(Sort.Order.asc("id")));
    }

    private static Sort.Order order(String item) {
        String[] parts = item.split(",", 2);
        String property = SORTABLE.get(parts[0].strip());
        if (property == null) {
            throw new InvalidFieldException(
                    "sort",
                    "unknown sort field '" + parts[0].strip() + "'; use one of " + SORTABLE.keySet() + " or sla_due");
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
