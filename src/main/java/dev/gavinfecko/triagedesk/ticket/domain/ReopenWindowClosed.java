package dev.gavinfecko.triagedesk.ticket.domain;

import dev.gavinfecko.triagedesk.common.errors.DomainRuleException;
import java.time.Duration;

/** A resolved ticket can be reopened for a while; after that the problem gets a fresh ticket. */
public class ReopenWindowClosed extends DomainRuleException {

    public static final Duration WINDOW = Duration.ofDays(14);

    public ReopenWindowClosed(String key) {
        super(
                "reopen-window-closed",
                "Ticket " + key + " was resolved more than " + WINDOW.toDays()
                        + " days ago and can no longer be reopened; open a new ticket and mention " + key);
    }
}
