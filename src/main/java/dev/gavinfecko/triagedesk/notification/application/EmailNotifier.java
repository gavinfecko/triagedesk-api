package dev.gavinfecko.triagedesk.notification.application;

import dev.gavinfecko.triagedesk.identity.application.UserDirectory;
import dev.gavinfecko.triagedesk.notification.infra.MailGateway;
import dev.gavinfecko.triagedesk.sla.domain.SlaBreached;
import dev.gavinfecko.triagedesk.sla.domain.SlaBreachesFound;
import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.TicketFacts;
import dev.gavinfecko.triagedesk.ticket.domain.CommentAdded;
import dev.gavinfecko.triagedesk.ticket.domain.TicketAssigned;
import dev.gavinfecko.triagedesk.ticket.domain.TicketCreated;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatusChanged;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns ticket and SLA events into emails, after the change has committed and on the mail pool: a rolled-back
 * change sends nothing, a slow or failing mail server never slows or undoes a change, and nobody is emailed about
 * their own action.
 */
@Service
public class EmailNotifier {

    private final TicketService tickets;
    private final UserDirectory people;
    private final MailTemplates templates;
    private final MailGateway mail;

    public EmailNotifier(TicketService tickets, UserDirectory people, MailTemplates templates, MailGateway mail) {
        this.tickets = tickets;
        this.people = people;
        this.templates = templates;
        this.mail = mail;
    }

    @Async(NotificationConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(TicketCreated event) {
        ticket(event.ticketId())
                .ifPresent(t -> people.contact(t.requesterId())
                        .ifPresent(requester -> mail.send(templates.created(t, requester))));
    }

    @Async(NotificationConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssigned(TicketAssigned event) {
        if (event.to() == null || event.to().equals(event.actorId())) {
            return;
        }
        ticket(event.ticketId())
                .ifPresent(t ->
                        people.contact(event.to()).ifPresent(assignee -> mail.send(templates.assigned(t, assignee))));
    }

    /** A public reply goes to the other party: the requester's to the assignee, staff's to the requester. */
    @Async(NotificationConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onComment(CommentAdded event) {
        if (event.visibility() != Visibility.PUBLIC) {
            return;
        }
        ticket(event.ticketId()).ifPresent(t -> {
            UUID recipient = event.authorId().equals(t.requesterId()) ? t.assigneeId() : t.requesterId();
            if (recipient == null || recipient.equals(event.authorId())) {
                return;
            }
            String author = people.displayNames(List.of(event.authorId())).getOrDefault(event.authorId(), "Someone");
            people.contact(recipient).ifPresent(to -> mail.send(templates.reply(t, to, author)));
        });
    }

    @Async(NotificationConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(TicketStatusChanged event) {
        boolean resolved = event.to() == TicketStatus.RESOLVED;
        boolean autoClosed = event.to() == TicketStatus.CLOSED && event.actorId() == null;
        if (!resolved && !autoClosed) {
            return;
        }
        ticket(event.ticketId())
                .ifPresent(t -> people.contact(t.requesterId())
                        .ifPresent(requester -> mail.send(
                                resolved ? templates.resolved(t, requester) : templates.autoClosed(t, requester))));
    }

    /** The assignee hears about their own ticket's breach. */
    @Async(NotificationConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBreach(SlaBreached event) {
        ticket(event.ticketId()).ifPresent(t -> {
            if (t.assigneeId() != null) {
                people.contact(t.assigneeId())
                        .ifPresent(to ->
                                mail.send(templates.breached(t, to, clockName(event), event.escalatedTo() != null)));
            }
        });
    }

    /** Every active admin gets one digest per scan run listing all breaches. */
    @Async(NotificationConfig.MAIL_EXECUTOR)
    @EventListener
    public void onBreaches(SlaBreachesFound event) {
        List<MailTemplates.Breach> lines = event.breaches().stream()
                .flatMap(b -> ticket(b.ticketId()).stream()
                        .map(t -> new MailTemplates.Breach(t, clockName(b), b.escalatedTo() != null)))
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        people.activeAdmins().forEach(admin -> mail.send(templates.breachDigest(lines, admin)));
    }

    private static String clockName(SlaBreached breach) {
        return breach.kind() == Kind.FIRST_RESPONSE ? "First response" : "Resolution";
    }

    private Optional<TicketFacts> ticket(@Nullable UUID id) {
        return id == null ? Optional.empty() : tickets.facts(id);
    }
}
