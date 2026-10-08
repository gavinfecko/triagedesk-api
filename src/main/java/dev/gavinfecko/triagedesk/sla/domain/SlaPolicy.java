package dev.gavinfecko.triagedesk.sla.domain;

import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * What a priority promises: a first-response budget and a resolution budget, counted on a calendar. Timers copy
 * these budgets when a ticket is created, so an edit here changes only tickets opened afterwards. The
 * budget rules (first response at least a minute, resolution longer) are checked by the service and the database.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SlaPolicy() {}

    public UUID id() {
        return id;
    }

    public Priority priority() {
        return priority;
    }

    public int firstResponseMinutes() {
        return firstResponseMinutes;
    }

    public int resolutionMinutes() {
        return resolutionMinutes;
    }

    public UUID calendarId() {
        return calendarId;
    }

    public boolean active() {
        return active;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void update(
            int newFirstResponseMinutes, int newResolutionMinutes, UUID newCalendarId, boolean nowActive, Instant now) {
        this.firstResponseMinutes = newFirstResponseMinutes;
        this.resolutionMinutes = newResolutionMinutes;
        this.calendarId = newCalendarId;
        this.active = nowActive;
        this.updatedAt = now;
    }
}
