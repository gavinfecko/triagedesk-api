package dev.gavinfecko.triagedesk.sla.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A calendar as stored: the editable form of {@link BusinessCalendar}. */
@Entity
@Table(name = "business_calendars")
public class CalendarDefinition {

    @Embeddable
    public record HolidayEntry(
            @Column(name = "holiday", nullable = false) LocalDate date,
            @Column(nullable = false) String name) {}

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String zone;

    @Column(name = "always_open", nullable = false)
    private boolean alwaysOpen;

    /** {@code {"MON": ["08:00", "18:00"], ...}}; parsed by {@code CalendarCodec}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String hours;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "calendar_holidays", joinColumns = @JoinColumn(name = "calendar_id"))
    private Set<HolidayEntry> holidays = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalendarDefinition() {}

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String zone() {
        return zone;
    }

    public boolean alwaysOpen() {
        return alwaysOpen;
    }

    public String hours() {
        return hours;
    }

    public Set<HolidayEntry> holidays() {
        return Set.copyOf(holidays);
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void update(
            String newName, String newZone, String newHoursJson, Set<HolidayEntry> newHolidays, Instant now) {
        this.name = newName.strip();
        this.zone = newZone;
        this.hours = newHoursJson;
        this.holidays.clear();
        this.holidays.addAll(newHolidays);
        this.updatedAt = now;
    }
}
