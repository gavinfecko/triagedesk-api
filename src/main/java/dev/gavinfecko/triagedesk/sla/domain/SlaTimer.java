package dev.gavinfecko.triagedesk.sla.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * One SLA clock on one ticket. It starts with a copy of its policy and a due instant computed on the policy's
 * calendar; pausing freezes it, resuming pushes the due instant out by the business time the pause lasted.
 * All instants are UTC; the calendar's zone matters only inside {@link BusinessCalendar}.
 */
@Entity
@Table(name = "sla_timers")
public class SlaTimer {

    public enum Kind {
        FIRST_RESPONSE,
        RESOLUTION
    }

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private String policySnapshot;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** From this instant a quarter of the budget or less is left: the clock reads at_risk (SQL can filter on it). */
    @Column(name = "at_risk_at", nullable = false)
    private Instant atRiskAt;

    @Column(name = "paused_at")
    private @Nullable Instant pausedAt;

    @Column(name = "paused_total_seconds", nullable = false)
    private long pausedTotalSeconds;

    @Column(name = "met_at")
    private @Nullable Instant metAt;

    @Column(name = "breached_at")
    private @Nullable Instant breachedAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    protected SlaTimer() {}

    private SlaTimer(
            UUID ticketId, Kind kind, String policySnapshot, Instant startedAt, Instant dueAt, Instant atRiskAt) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.kind = kind;
        this.policySnapshot = policySnapshot;
        this.startedAt = startedAt;
        this.dueAt = dueAt;
        this.atRiskAt = atRiskAt;
    }

    public static SlaTimer start(
            UUID ticketId,
            Kind kind,
            String policySnapshot,
            Instant startedAt,
            BusinessCalendar calendar,
            Duration budget) {
        Instant due = calendar.add(startedAt, budget);
        return new SlaTimer(
                ticketId, kind, policySnapshot, startedAt, due, atRiskPoint(startedAt, due, budget, calendar));
    }

    /**
     * The instant from which only a quarter of the budget is left before {@code due}, measured from {@code ref}:
     * {@code ref} itself when that point has already passed.
     */
    public static Instant atRiskPoint(Instant ref, Instant due, Duration budget, BusinessCalendar calendar) {
        Duration remaining = ref.isAfter(due) ? Duration.ZERO : calendar.elapsed(ref, due);
        Duration quarter = budget.dividedBy(4);
        return remaining.compareTo(quarter) > 0 ? calendar.add(ref, remaining.minus(quarter)) : ref;
    }

    /** Still counting (or paused): not met, not breached by the scan, not cancelled. */
    public boolean isOpen() {
        return metAt == null && breachedAt == null && cancelledAt == null;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    public void pause(Instant at) {
        if (pausedAt == null) {
            pausedAt = at;
        }
    }

    /**
     * Ends a pause: the business time it lasted joins the total and pushes the due instant out by as much; the
     * at-risk point moves with it.
     */
    public Duration resume(Instant at, BusinessCalendar calendar, Duration budget) {
        Duration paused = endPause(at, calendar);
        atRiskAt = atRiskPoint(at, dueAt, budget, calendar);
        return paused;
    }

    private Duration endPause(Instant at, BusinessCalendar calendar) {
        if (pausedAt == null) {
            return Duration.ZERO;
        }
        Duration paused = calendar.elapsed(pausedAt, at);
        pausedTotalSeconds += paused.toSeconds();
        if (!paused.isZero()) {
            dueAt = calendar.add(dueAt, paused);
        }
        pausedAt = null;
        return paused;
    }

    public void meet(Instant at, BusinessCalendar calendar) {
        endPause(at, calendar);
        metAt = at;
    }

    /** Marked by the breach scan; a breached clock is never selected again. */
    public void breach(Instant at) {
        breachedAt = at;
    }

    public void cancel(Instant at) {
        cancelledAt = at;
    }

    /** A new policy: the due instant is recomputed from the original start, keeping the pauses already taken. */
    public void reschedule(String newPolicySnapshot, Instant newDueAt, Instant newAtRiskAt) {
        this.policySnapshot = newPolicySnapshot;
        this.dueAt = newDueAt;
        this.atRiskAt = newAtRiskAt;
    }

    /** Mirrors the SQL function {@code ticket_sla_status} (V15), which the ticket list filters on. */
    public SlaStatus status(Instant now) {
        if (cancelledAt != null) {
            return SlaStatus.CANCELLED;
        }
        if (breachedAt != null) {
            return SlaStatus.BREACHED;
        }
        if (metAt != null) {
            return metAt.isAfter(dueAt) ? SlaStatus.BREACHED : SlaStatus.MET;
        }
        if (pausedAt != null) {
            return SlaStatus.PAUSED;
        }
        if (now.isAfter(dueAt)) {
            return SlaStatus.BREACHED;
        }
        return now.isBefore(atRiskAt) ? SlaStatus.ON_TRACK : SlaStatus.AT_RISK;
    }

    /** Business time left before the due instant; frozen while paused, zero once the clock has stopped. */
    public Duration remaining(BusinessCalendar calendar, Instant now) {
        if (!isOpen()) {
            return Duration.ZERO;
        }
        Instant from = pausedAt != null ? pausedAt : now;
        return from.isAfter(dueAt) ? Duration.ZERO : calendar.elapsed(from, dueAt);
    }

    public UUID id() {
        return id;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public Kind kind() {
        return kind;
    }

    public String policySnapshot() {
        return policySnapshot;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public Instant atRiskAt() {
        return atRiskAt;
    }

    public @Nullable Instant pausedAt() {
        return pausedAt;
    }

    public long pausedTotalSeconds() {
        return pausedTotalSeconds;
    }

    public @Nullable Instant metAt() {
        return metAt;
    }

    public @Nullable Instant breachedAt() {
        return breachedAt;
    }

    public @Nullable Instant cancelledAt() {
        return cancelledAt;
    }
}
