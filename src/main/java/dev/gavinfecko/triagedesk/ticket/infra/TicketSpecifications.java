package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.ticket.application.TicketQuery;
import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/** Turns a {@link TicketQuery} into a WHERE clause. The caller decides the visibility scope. */
public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<Ticket> matching(
            TicketQuery q, UUID caller, @Nullable UUID onlyRequester, @Nullable UUID tagId) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (onlyRequester != null) {
                and.add(cb.equal(root.get("requesterId"), onlyRequester));
            } else if (q.requesterId() != null) {
                and.add(cb.equal(root.get("requesterId"), q.requesterId()));
            }
            if (q.status() != null && !q.status().isEmpty()) {
                and.add(root.get("status").in(q.status()));
            }
            if (q.priority() != null && !q.priority().isEmpty()) {
                and.add(root.get("priority").in(q.priority()));
            }
            if (q.queueId() != null) {
                and.add(cb.equal(root.get("queueId"), q.queueId()));
            }
            if (q.categoryId() != null) {
                and.add(cb.equal(root.get("categoryId"), q.categoryId()));
            }
            if (tagId != null) {
                and.add(cb.equal(root.join("tagIds"), tagId));
            }
            if (q.assignee() != null) {
                and.add(
                        switch (q.assignee()) {
                            case "me" -> cb.equal(root.get("assigneeId"), caller);
                            case "unassigned" -> cb.isNull(root.get("assigneeId"));
                            default -> cb.equal(root.get("assigneeId"), parseUuid(q.assignee()));
                        });
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldException("assignee_id", "must be \"me\", \"unassigned\" or a user id");
        }
    }
}
