package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.sla.domain.SlaTimer.Kind;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.time.Duration;
import java.util.UUID;

/** The policy a timer was started with, stored on the timer as JSON so later policy edits leave it alone. */
public record PolicySnapshot(
        UUID policyId,
        Priority priority,
        int firstResponseMinutes,
        int resolutionMinutes,
        UUID calendarId,
        String calendarName) {

    public static PolicySnapshot of(SlaPolicyView policy) {
        return new PolicySnapshot(
                policy.id(),
                policy.priority(),
                policy.firstResponseMinutes(),
                policy.resolutionMinutes(),
                policy.calendarId(),
                policy.calendarName());
    }

    public Duration budgetFor(Kind kind) {
        return Duration.ofMinutes(kind == Kind.FIRST_RESPONSE ? firstResponseMinutes : resolutionMinutes);
    }
}
