package dev.gavinfecko.triagedesk.notification.application;

import dev.gavinfecko.triagedesk.identity.application.UserDirectory.Contact;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.TicketFacts;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** The wording of every notification. Each one carries the ticket key, title, status and a link to the ticket. */
@Component
public class MailTemplates {

    private final String baseUrl;

    public MailTemplates(@Value("${triagedesk.app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public Email created(TicketFacts t, Contact to) {
        return build(
                t,
                to,
                "We received your ticket",
                "We received your ticket and it is in the queue. "
                        + "Reply on the ticket page if you have more details.");
    }

    public Email assigned(TicketFacts t, Contact to) {
        return build(t, to, "Assigned to you", "This ticket is now assigned to you.");
    }

    public Email reply(TicketFacts t, Contact to, String author) {
        return build(
                t, to, "New reply from " + author, author + " replied on this ticket. Open it to read and answer.");
    }

    public Email resolved(TicketFacts t, Contact to) {
        return build(
                t,
                to,
                "Resolved: please confirm or reopen",
                "We marked this ticket resolved. If the problem is "
                        + "fixed there is nothing to do; it closes by itself after three business days. If it is not fixed, "
                        + "reopen it from the ticket page.");
    }

    public Email autoClosed(TicketFacts t, Contact to) {
        return build(
                t,
                to,
                "Closed",
                "This ticket was resolved three business days ago and has now been closed. "
                        + "If the problem comes back, open a new ticket.");
    }

    public Email breached(TicketFacts t, Contact to, String clock, boolean escalated) {
        String what = clock + " target missed.";
        return build(
                t,
                to,
                "SLA breached: " + clock,
                escalated ? what + " The ticket's priority was raised one level." : what);
    }

    /** One line of an admin digest. */
    public record Breach(TicketFacts ticket, String clock, boolean escalated) {}

    public Email breachDigest(List<Breach> breaches, Contact to) {
        String subject =
                "[TriageDesk] SLA breached on " + breaches.size() + (breaches.size() == 1 ? " ticket" : " tickets");
        StringBuilder text =
                new StringBuilder("Hello " + to.displayName() + ",\n\nThese SLA targets were just missed:\n\n");
        StringBuilder html = new StringBuilder(
                "<p>Hello " + esc(to.displayName()) + ",</p><p>These SLA targets were just missed:</p><ul>");
        for (Breach b : breaches) {
            String link = baseUrl + "/tickets/" + b.ticket().key();
            String note = b.escalated() ? " (priority raised)" : "";
            text.append("- ")
                    .append(b.ticket().key())
                    .append(" ")
                    .append(b.ticket().title())
                    .append(": ")
                    .append(b.clock())
                    .append(", status ")
                    .append(b.ticket().status())
                    .append(note)
                    .append("\n  ")
                    .append(link)
                    .append("\n");
            html.append("<li><a href=\"")
                    .append(esc(link))
                    .append("\">")
                    .append(esc(b.ticket().key()))
                    .append("</a> ")
                    .append(esc(b.ticket().title()))
                    .append(": ")
                    .append(esc(b.clock()))
                    .append(", status ")
                    .append(esc(b.ticket().status().name()))
                    .append(esc(note))
                    .append("</li>");
        }
        text.append("\n-- TriageDesk\n");
        html.append("</ul><p>-- TriageDesk</p>");
        return new Email(to.email(), subject, text.toString(), html.toString());
    }

    private Email build(TicketFacts t, Contact to, String headline, String message) {
        String link = baseUrl + "/tickets/" + t.key();
        String subject = "[" + t.key() + "] " + headline + ": " + t.title();
        String text = "Hello " + to.displayName() + ",\n\n" + message + "\n\n"
                + "Ticket: " + t.key() + "\n"
                + "Title:  " + t.title() + "\n"
                + "Status: " + t.status() + "\n\n"
                + link + "\n\n-- TriageDesk\n";
        String html = "<p>Hello " + esc(to.displayName()) + ",</p>"
                + "<p>" + esc(message) + "</p>"
                + "<table><tr><td>Ticket</td><td><strong>" + esc(t.key()) + "</strong></td></tr>"
                + "<tr><td>Title</td><td>" + esc(t.title()) + "</td></tr>"
                + "<tr><td>Status</td><td>" + esc(t.status().name()) + "</td></tr></table>"
                + "<p><a href=\"" + esc(link) + "\">Open " + esc(t.key()) + "</a></p>"
                + "<p>-- TriageDesk</p>";
        return new Email(to.email(), subject, text, html);
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}
