package dev.gavinfecko.triagedesk.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The aggregate root (ARCHITECTURE.md §4). People and reference data are held by id: other modules
 * own them, and the ticket only needs to point at them.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private UUID id;

    @Column(name = "ticket_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "assignee_id")
    private @Nullable UUID assigneeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "first_responded_at")
    private @Nullable Instant firstRespondedAt;

    @Column(name = "resolved_at")
    private @Nullable Instant resolvedAt;

    @Column(name = "closed_at")
    private @Nullable Instant closedAt;

    @Version
    private long version;

    protected Ticket() {}

    /** Every ticket starts life {@code NEW}. */
    public static Ticket open(
            String key,
            String title,
            String description,
            Priority priority,
            UUID categoryId,
            UUID queueId,
            UUID requesterId,
            Instant now) {
        Ticket ticket = new Ticket();
        ticket.id = UUID.randomUUID();
        ticket.key = key;
        ticket.title = title.strip();
        ticket.description = description.strip();
        ticket.status = TicketStatus.NEW;
        ticket.priority = priority;
        ticket.categoryId = categoryId;
        ticket.queueId = queueId;
        ticket.requesterId = requesterId;
        ticket.createdAt = now;
        ticket.updatedAt = now;
        return ticket;
    }

    /**
     * Applies a transition the table has already approved and keeps the lifecycle timestamps honest.
     * Callers check {@link TicketStatus#canTransition} first; this method only records the move.
     */
    public void transitionTo(TicketStatus to, Instant now) {
        switch (to) {
            case OPEN -> {
                if (status == TicketStatus.NEW && firstRespondedAt == null) {
                    firstRespondedAt = now;
                }
                if (status == TicketStatus.RESOLVED) { // reopen: the resolution is withdrawn
                    resolvedAt = null;
                    closedAt = null;
                }
            }
            case RESOLVED -> resolvedAt = now;
            case CLOSED -> closedAt = now;
            case NEW, PENDING, CANCELLED -> {
                // no timestamp of their own
            }
        }
        status = to;
        updatedAt = now;
    }

    /** Staff's first public reply. Idempotent; status is untouched (assignment opens a NEW ticket, TD-24). */
    public boolean recordFirstResponse(Instant now) {
        if (firstRespondedAt != null) {
            return false;
        }
        firstRespondedAt = now;
        updatedAt = now;
        return true;
    }

    /** A requester's reply ends the wait: PENDING goes back to OPEN. Returns the previous status, or null if nothing changed. */
    public @Nullable TicketStatus returnToOpenIfPending(Instant now) {
        if (status != TicketStatus.PENDING) {
            return null;
        }
        TicketStatus before = status;
        status = TicketStatus.OPEN;
        updatedAt = now;
        return before;
    }

    public UUID id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public TicketStatus status() {
        return status;
    }

    public Priority priority() {
        return priority;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public UUID queueId() {
        return queueId;
    }

    public UUID requesterId() {
        return requesterId;
    }

    public @Nullable UUID assigneeId() {
        return assigneeId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant firstRespondedAt() {
        return firstRespondedAt;
    }

    public @Nullable Instant resolvedAt() {
        return resolvedAt;
    }

    public @Nullable Instant closedAt() {
        return closedAt;
    }

    public long version() {
        return version;
    }
}
