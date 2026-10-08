package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.ApiException;
import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.common.web.PageResponse;
import dev.gavinfecko.triagedesk.common.web.Preconditions;
import dev.gavinfecko.triagedesk.identity.application.UserDirectory;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Ref;
import dev.gavinfecko.triagedesk.ticket.domain.Category;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.Queue;
import dev.gavinfecko.triagedesk.ticket.domain.ReopenWindowClosed;
import dev.gavinfecko.triagedesk.ticket.domain.Tag;
import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import dev.gavinfecko.triagedesk.ticket.domain.TicketAssigned;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
import dev.gavinfecko.triagedesk.ticket.domain.TicketPriorityChanged;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStateConflict;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import dev.gavinfecko.triagedesk.ticket.infra.CategoryRepository;
import dev.gavinfecko.triagedesk.ticket.infra.QueueRepository;
import dev.gavinfecko.triagedesk.ticket.infra.TagRepository;
import dev.gavinfecko.triagedesk.ticket.infra.TicketKeys;
import dev.gavinfecko.triagedesk.ticket.infra.TicketRepository;
import dev.gavinfecko.triagedesk.ticket.infra.TicketSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The one place tickets change (ARCHITECTURE.md §4) and the one place ticket events are published. */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final CategoryRepository categories;
    private final QueueRepository queues;
    private final TagRepository tags;
    private final TicketKeys keys;
    private final UserDirectory people;
    private final CommentService comments;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final TicketSlaLookup sla;
    private final Clock clock;

    public TicketService(
            TicketRepository tickets,
            CategoryRepository categories,
            QueueRepository queues,
            TagRepository tags,
            TicketKeys keys,
            UserDirectory people,
            CommentService comments,
            AuditLog audit,
            ApplicationEventPublisher events,
            TicketSlaLookup sla,
            Clock clock) {
        this.tickets = tickets;
        this.categories = categories;
        this.queues = queues;
        this.tags = tags;
        this.keys = keys;
        this.people = people;
        this.comments = comments;
        this.audit = audit;
        this.events = events;
        this.sla = sla;
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

    /**
     * Moves a ticket through the state machine. The table decides what the caller's role may do from
     * the current status; moves to PENDING and RESOLVED must carry a comment, which is stored as a
     * public reply so the requester sees why they are waiting or what was done.
     */
    @Transactional
    public TicketView transition(String key, TicketStatus to, @Nullable String comment) {
        CurrentUser actor = CurrentUser.get();
        Ticket ticket = visibleTicket(key, actor);
        TicketStatus from = ticket.status();
        TicketStatus.Transition move = from.transitionTo(to)
                .filter(t -> t.allows(actor.role()))
                .orElseThrow(() -> new TicketStateConflict(key, from, to, actor.role()));
        boolean hasComment = comment != null && !comment.isBlank();
        if (move.commentRequired() && !hasComment) {
            throw new InvalidFieldException("comment", "required when moving a ticket to " + to);
        }
        Instant now = clock.instant();
        if (from == TicketStatus.RESOLVED && to == TicketStatus.OPEN) {
            Instant resolvedAt = ticket.resolvedAt();
            if (resolvedAt != null && resolvedAt.plus(ReopenWindowClosed.WINDOW).isBefore(now)) {
                throw new ReopenWindowClosed(key);
            }
        }
        ticket.transitionTo(to, now);
        audit.record(AuditEvent.of("ticket.status_changed")
                .actor(actor.id())
                .ticket(ticket.id())
                .change("status", from, to));
        events.publishEvent(new TicketStatusChanged(ticket.id(), ticket.key(), from, to, actor.id(), now));
        if (hasComment) {
            comments.write(ticket, actor, Visibility.PUBLIC, comment, now);
        }
        return view(ticket, List.of());
    }

    /**
     * Assigns a ticket: {@code "me"}, another active agent or admin, or {@code null} to unassign.
     * Taking a NEW ticket opens it, which also counts as the first response.
     */
    @Transactional
    public TicketView assign(String key, @Nullable String assignee) {
        CurrentUser actor = requireStaff();
        Ticket ticket = workable(key, actor);
        UUID to = resolveAssignee(assignee, actor);
        UUID from = ticket.assigneeId();
        Instant now = clock.instant();
        ticket.assign(to, now);
        audit.record(AuditEvent.of("ticket.assigned")
                .actor(actor.id())
                .ticket(ticket.id())
                .change("assignee_id", from, to));
        events.publishEvent(new TicketAssigned(ticket.id(), ticket.key(), from, to, actor.id(), now));
        if (to != null && ticket.status() == TicketStatus.NEW) {
            ticket.transitionTo(TicketStatus.OPEN, now);
            audit.record(AuditEvent.of("ticket.status_changed")
                    .actor(actor.id())
                    .ticket(ticket.id())
                    .change("status", TicketStatus.NEW, TicketStatus.OPEN));
            events.publishEvent(new TicketStatusChanged(
                    ticket.id(), ticket.key(), TicketStatus.NEW, TicketStatus.OPEN, actor.id(), now));
        }
        return view(ticket, List.of());
    }

    @Transactional
    public TicketView moveToQueue(String key, UUID queueId) {
        CurrentUser actor = requireStaff();
        Ticket ticket = workable(key, actor);
        Queue queue =
                queues.findById(queueId).orElseThrow(() -> new InvalidFieldException("queue_id", "no such queue"));
        UUID from = ticket.queueId();
        if (!from.equals(queue.id())) {
            ticket.moveToQueue(queue.id(), clock.instant());
            audit.record(AuditEvent.of("ticket.queue_changed")
                    .actor(actor.id())
                    .ticket(ticket.id())
                    .change("queue_id", from, queue.id()));
        }
        return view(ticket, List.of());
    }

    public record TicketEdits(
            @Nullable String title,
            @Nullable String description,
            @Nullable Priority priority,
            @Nullable UUID categoryId) {
        boolean touchesStaffFields() {
            return priority != null || categoryId != null;
        }
    }

    /**
     * Edits a ticket, guarded by the version the caller last read. Staff may reword, re-prioritise and
     * re-categorise any open ticket; a requester may reword their own ticket only while it is NEW, and
     * never touch priority or category.
     */
    @Transactional
    public TicketView update(String key, TicketEdits edits, long expectedVersion) {
        CurrentUser actor = CurrentUser.get();
        Ticket ticket = visibleTicket(key, actor);
        if (ticket.status().isTerminal()) {
            throw new TicketStateConflict(key, ticket.status(), "it is closed to further changes");
        }
        if (!actor.isStaff() && ticket.status() != TicketStatus.NEW) {
            throw new AccessDeniedException("Requesters can edit a ticket only while it is NEW");
        }
        if (!actor.isStaff() && edits.touchesStaffFields()) {
            throw new AccessDeniedException("Only agents and admins change priority or category");
        }
        Category newCategory = edits.categoryId() == null
                ? null
                : categories
                        .findById(edits.categoryId())
                        .orElseThrow(() -> new InvalidFieldException("category_id", "no such category"));
        if (ticket.version() != expectedVersion) {
            throw Preconditions.stale();
        }
        Instant now = clock.instant();
        boolean changed = false;
        if (edits.priority() != null && edits.priority() != ticket.priority()) {
            Priority before = ticket.priority();
            ticket.changePriority(edits.priority(), now);
            audit.record(AuditEvent.of("ticket.priority_changed")
                    .actor(actor.id())
                    .ticket(ticket.id())
                    .change("priority", before, ticket.priority()));
            events.publishEvent(
                    new TicketPriorityChanged(ticket.id(), ticket.key(), before, ticket.priority(), actor.id(), now));
            changed = true;
        }
        if (newCategory != null && !newCategory.id().equals(ticket.categoryId())) {
            UUID before = ticket.categoryId();
            ticket.recategorize(newCategory.id(), now);
            audit.record(AuditEvent.of("ticket.category_changed")
                    .actor(actor.id())
                    .ticket(ticket.id())
                    .change("category_id", before, newCategory.id()));
            changed = true;
        }
        String titleBefore = ticket.title();
        String descriptionBefore = ticket.description();
        if (ticket.edit(edits.title(), edits.description(), now)) {
            changed = true;
            if (!titleBefore.equals(ticket.title())) {
                audit.record(AuditEvent.of("ticket.edited")
                        .actor(actor.id())
                        .ticket(ticket.id())
                        .change("title", titleBefore, ticket.title()));
            }
            if (!descriptionBefore.equals(ticket.description())) {
                audit.record(AuditEvent.of("ticket.edited")
                        .actor(actor.id())
                        .ticket(ticket.id())
                        .change("description", descriptionBefore, ticket.description()));
            }
        }
        if (changed) {
            tickets.flush(); // bumps the version now, so the response carries the new ETag
        }
        return view(ticket, List.of());
    }

    private @Nullable UUID resolveAssignee(@Nullable String assignee, CurrentUser actor) {
        if (assignee == null) {
            return null;
        }
        if (assignee.equals("me")) {
            return actor.id();
        }
        UUID id;
        try {
            id = UUID.fromString(assignee);
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidFieldException("assignee_id", "must be \"me\", a user id, or null");
        }
        if (!people.isActiveStaff(id)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-assignee",
                    "Invalid assignee",
                    "Tickets can only be assigned to an active agent or admin");
        }
        return id;
    }

    /**
     * SLA escalation: raises a ticket one priority level as the system (no actor), audited and announced like a
     * person's change so the SLA clocks reschedule. Finished tickets and P1 are left alone.
     */
    @Transactional
    public Optional<Priority> escalate(UUID ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        Priority before = ticket.priority();
        Priority after = before.raised();
        if (ticket.status().isTerminal() || after == before) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        ticket.changePriority(after, now);
        audit.record(
                AuditEvent.of("ticket.priority_changed").ticket(ticket.id()).change("priority", before, after));
        events.publishEvent(new TicketPriorityChanged(ticket.id(), ticket.key(), before, after, null, now));
        return Optional.of(after);
    }

    private static CurrentUser requireStaff() {
        CurrentUser actor = CurrentUser.get();
        if (!actor.isStaff()) {
            throw new AccessDeniedException("Only agents and admins work tickets");
        }
        return actor;
    }

    /** A staff member's ticket to work on: must exist and must not be closed or cancelled. */
    private Ticket workable(String key, CurrentUser actor) {
        Ticket ticket = visibleTicket(key, actor);
        if (ticket.status().isTerminal()) {
            throw new TicketStateConflict(key, ticket.status(), "it is closed to further changes");
        }
        return ticket;
    }

    private Ticket visibleTicket(String key, CurrentUser actor) {
        return tickets.findByKey(key)
                .filter(t -> actor.isStaff() || t.requesterId().equals(actor.id()))
                .orElseThrow(() -> new NotFoundException("Ticket", key));
    }

    /**
     * One ticket. A requester asking for someone else's ticket gets the same 404 as for a key that
     * does not exist, so ticket keys cannot be probed. The check is here, not in the controller.
     */
    @Transactional(readOnly = true)
    public TicketView get(String key) {
        return view(visibleTicket(key, CurrentUser.get()), List.of());
    }

    /**
     * The ticket list. Filters combine with AND, values within a filter with OR; sort fields are
     * allow-listed; page size is capped. Requesters are silently scoped to their own tickets, and
     * their {@code requester_id} filter is ignored; agents and admins see everything.
     */
    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> list(TicketQuery query) {
        CurrentUser actor = CurrentUser.get();
        Pageable request = PageResponse.request(query.page(), query.size(), query.toSort());
        UUID tagId = null;
        if (query.tag() != null) {
            String name = Tag.normalize(query.tag());
            tagId = name == null ? null : tags.findByName(name).map(Tag::id).orElse(null);
            if (tagId == null) { // an unknown tag matches nothing, which is an empty page, not an error
                return PageResponse.of(Page.empty(request), t -> null);
            }
        }
        Page<Ticket> found = tickets.findAll(
                TicketSpecifications.matching(
                        query, actor.id(), actor.isStaff() ? null : actor.id(), tagId, clock.instant()),
                request);

        Set<UUID> personIds = new HashSet<>();
        found.forEach(t -> {
            personIds.add(t.requesterId());
            if (t.assigneeId() != null) {
                personIds.add(t.assigneeId());
            }
        });
        Map<UUID, String> names = people.displayNames(personIds);
        Map<UUID, String> categoryNames =
                categories.findAll().stream().collect(Collectors.toMap(Category::id, Category::name));
        Map<UUID, String> queueNames = queues.findAll().stream().collect(Collectors.toMap(Queue::id, Queue::name));
        Function<UUID, Person> person = id -> id == null ? null : new Person(id, names.get(id));
        Map<UUID, TicketSummary.Sla> clocks =
                sla.forTickets(found.map(Ticket::id).toList());
        return PageResponse.of(
                found,
                t -> new TicketSummary(
                        t.id(),
                        t.key(),
                        t.title(),
                        t.status(),
                        t.priority(),
                        categoryNames.get(t.categoryId()),
                        queueNames.get(t.queueId()),
                        person.apply(t.requesterId()),
                        person.apply(t.assigneeId()),
                        t.createdAt(),
                        t.updatedAt(),
                        clocks.get(t.id())));
    }

    /**
     * Replaces a ticket's tags (staff). Names are normalised; unknown tags are created. The whole set
     * is audited as one change so a reader sees "was [a, b], now [a, c]".
     */
    @Transactional
    public TicketView replaceTags(String key, List<String> rawNames) {
        CurrentUser actor = requireStaff();
        Ticket ticket = workable(key, actor);
        Set<String> names = new java.util.TreeSet<>();
        for (String raw : rawNames) {
            String name = Tag.normalize(raw);
            if (name == null) {
                throw new InvalidFieldException(
                        "tags",
                        "'" + raw
                                + "' is not a tag: 2–30 letters, digits or dashes, not starting or ending with a dash");
            }
            names.add(name);
        }
        Map<String, Tag> existing = tags.findByNameIn(names).stream().collect(Collectors.toMap(Tag::name, t -> t));
        Set<UUID> ids = new HashSet<>();
        for (String name : names) {
            ids.add(existing.computeIfAbsent(name, n -> tags.save(new Tag(n))).id());
        }
        List<String> before = tagNames(ticket);
        if (ticket.retag(ids, clock.instant())) {
            audit.record(AuditEvent.of("ticket.tags_changed")
                    .actor(actor.id())
                    .ticket(ticket.id())
                    .change("tags", before, List.copyOf(names)));
        }
        return view(ticket, List.of());
    }

    private List<String> tagNames(Ticket ticket) {
        if (ticket.tagIds().isEmpty()) {
            return List.of();
        }
        return tags.findAllById(ticket.tagIds()).stream()
                .map(Tag::name)
                .sorted()
                .toList();
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
                ticket.reopenCount(),
                tagNames(ticket),
                ticket.version(),
                List.copyOf(warnings));
    }
}
