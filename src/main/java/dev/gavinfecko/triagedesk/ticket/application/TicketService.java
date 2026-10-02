package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.identity.application.UserDirectory;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Ref;
import dev.gavinfecko.triagedesk.ticket.domain.Category;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.Queue;
import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
import dev.gavinfecko.triagedesk.ticket.infra.CategoryRepository;
import dev.gavinfecko.triagedesk.ticket.infra.QueueRepository;
import dev.gavinfecko.triagedesk.ticket.infra.TicketKeys;
import dev.gavinfecko.triagedesk.ticket.infra.TicketRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The one place tickets change (ARCHITECTURE.md §4) and the one place ticket events are published. */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final CategoryRepository categories;
    private final QueueRepository queues;
    private final TicketKeys keys;
    private final UserDirectory people;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TicketService(
            TicketRepository tickets,
            CategoryRepository categories,
            QueueRepository queues,
            TicketKeys keys,
            UserDirectory people,
            AuditLog audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.tickets = tickets;
        this.categories = categories;
        this.queues = queues;
        this.keys = keys;
        this.people = people;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record NewTicket(
            String title,
            String description,
            UUID categoryId,
            Priority priority,
            @Nullable UUID requesterId,
            @Nullable UUID queueId) {}

    /**
     * Opens a ticket. Staff may open it on someone's behalf ("phoned in") and pick the queue; a
     * requester's ticket is always their own and routed by category, and any staff-only fields they
     * send are ignored with a warning rather than an error.
     */
    @Transactional
    public TicketView create(NewTicket request) {
        CurrentUser actor = CurrentUser.get();
        List<String> warnings = new ArrayList<>();
        Category category = categories
                .findById(request.categoryId())
                .orElseThrow(() -> new InvalidFieldException("category_id", "no such category"));

        UUID requester = actor.id();
        UUID queueId = category.defaultQueueId();
        if (actor.isStaff()) {
            if (request.requesterId() != null) {
                if (!people.isActive(request.requesterId())) {
                    throw new InvalidFieldException("requester_id", "no active user with this id");
                }
                requester = request.requesterId();
            }
            if (request.queueId() != null) {
                queueId = queues.findById(request.queueId())
                        .orElseThrow(() -> new InvalidFieldException("queue_id", "no such queue"))
                        .id();
            }
        } else {
            if (request.requesterId() != null && !request.requesterId().equals(actor.id())) {
                warnings.add("requester_id is set to you for requesters; the value sent was ignored");
            }
            if (request.queueId() != null) {
                warnings.add("queue_id is chosen by category for requesters; the value sent was ignored");
            }
        }

        Ticket ticket = tickets.saveAndFlush(Ticket.open(
                keys.next(),
                request.title(),
                request.description(),
                request.priority(),
                category.id(),
                queueId,
                requester,
                clock.instant()));
        audit.record(AuditEvent.of("ticket.created")
                .actor(actor.id())
                .ticket(ticket.id())
                .change("status", null, ticket.status()));
        events.publishEvent(new TicketCreated(
                ticket.id(), ticket.key(), ticket.priority(), ticket.requesterId(), ticket.createdAt()));
        return view(ticket, warnings);
    }

    TicketView view(Ticket ticket, List<String> warnings) {
        Set<UUID> ids = new HashSet<>();
        ids.add(ticket.requesterId());
        if (ticket.assigneeId() != null) {
            ids.add(ticket.assigneeId());
        }
        Map<UUID, String> names = people.displayNames(ids);
        Category category = categories.getReferenceById(ticket.categoryId());
        Queue queue = queues.getReferenceById(ticket.queueId());
        return new TicketView(
                ticket.id(),
                ticket.key(),
                ticket.title(),
                ticket.description(),
                ticket.status(),
                ticket.priority(),
                new Ref(category.id(), category.name()),
                new Ref(queue.id(), queue.name()),
                new Person(ticket.requesterId(), names.get(ticket.requesterId())),
                ticket.assigneeId() == null ? null : new Person(ticket.assigneeId(), names.get(ticket.assigneeId())),
                ticket.createdAt(),
                ticket.updatedAt(),
                ticket.firstRespondedAt(),
                ticket.resolvedAt(),
                ticket.closedAt(),
                List.copyOf(warnings));
    }
}
