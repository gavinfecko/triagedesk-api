package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.identity.application.UserDirectory;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.domain.Comment;
import dev.gavinfecko.triagedesk.ticket.domain.CommentAdded;
import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import dev.gavinfecko.triagedesk.ticket.domain.TicketFirstResponded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import dev.gavinfecko.triagedesk.ticket.infra.CommentRepository;
import dev.gavinfecko.triagedesk.ticket.infra.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversation on a ticket. Requesters see and write PUBLIC comments on their own tickets; staff
 * write either kind on any ticket. Side effects of a reply (first response, PENDING → OPEN) live here
 * because they are facts about the conversation, not separate actions.
 */
@Service
public class CommentService {

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final UserDirectory people;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CommentService(
            TicketRepository tickets,
            CommentRepository comments,
            UserDirectory people,
            AuditLog audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.people = people;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CommentView add(String key, Visibility visibility, String body) {
        CurrentUser actor = CurrentUser.get();
        Ticket ticket = visibleTicket(key, actor);
        if (!actor.isStaff() && visibility == Visibility.INTERNAL) {
            throw new AccessDeniedException("Requesters cannot write internal notes");
        }
        Instant now = clock.instant();
        Comment comment = comments.save(new Comment(ticket.id(), actor.id(), visibility, body, now));
        audit.record(AuditEvent.of("ticket.comment_added")
                .actor(actor.id())
                .ticket(ticket.id())
                .change("visibility", null, visibility));
        events.publishEvent(new CommentAdded(ticket.id(), ticket.key(), comment.id(), actor.id(), visibility, now));

        if (visibility == Visibility.PUBLIC) {
            if (actor.isStaff() && isAwaitingFirstResponse(ticket) && ticket.recordFirstResponse(now)) {
                audit.record(
                        AuditEvent.of("ticket.first_response").actor(actor.id()).ticket(ticket.id()));
                events.publishEvent(new TicketFirstResponded(ticket.id(), ticket.key(), actor.id(), now));
            }
            if (!actor.isStaff()) {
                TicketStatus before = ticket.returnToOpenIfPending(now);
                if (before != null) {
                    audit.record(AuditEvent.of("ticket.status_changed")
                            .actor(actor.id())
                            .ticket(ticket.id())
                            .change("status", before, ticket.status()));
                    events.publishEvent(new TicketStatusChanged(
                            ticket.id(), ticket.key(), before, ticket.status(), actor.id(), now));
                }
            }
        }
        return view(
                comment,
                Map.of(actor.id(), people.displayNames(List.of(actor.id())).get(actor.id())));
    }

    @Transactional(readOnly = true)
    public List<CommentView> list(String key) {
        CurrentUser actor = CurrentUser.get();
        Ticket ticket = visibleTicket(key, actor);
        List<Comment> found = actor.isStaff()
                ? comments.findByTicketIdOrderByCreatedAtAsc(ticket.id())
                : comments.findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticket.id(), Visibility.PUBLIC);
        Map<UUID, String> names =
                people.displayNames(found.stream().map(Comment::authorId).collect(Collectors.toSet()));
        return found.stream().map(c -> view(c, names)).toList();
    }

    private static boolean isAwaitingFirstResponse(Ticket ticket) {
        return ticket.status() == TicketStatus.NEW || ticket.status() == TicketStatus.OPEN;
    }

    private Ticket visibleTicket(String key, CurrentUser actor) {
        return tickets.findByKey(key)
                .filter(t -> actor.isStaff() || t.requesterId().equals(actor.id()))
                .orElseThrow(() -> new NotFoundException("Ticket", key));
    }

    private static CommentView view(Comment c, Map<UUID, String> names) {
        return new CommentView(
                c.id(), new Person(c.authorId(), names.get(c.authorId())), c.visibility(), c.body(), c.createdAt());
    }
}
