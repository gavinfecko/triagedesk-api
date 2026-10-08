package dev.gavinfecko.triagedesk.sla.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Business time, as a pure value: which hours of which weekdays count, in which zone, minus
 * holidays. {@link #elapsed} measures business time between two instants; {@link #add} finds the
 * instant at which a business duration has passed. No clock, no Spring, no database, so the SLA
 * arithmetic can be tested exhaustively (ARCHITECTURE.md §5).
 *
 * <p>Instants are UTC; the zone is applied only here. A day's window is built with
 * {@link ZonedDateTime}, so a day that gains or loses an hour at a daylight-saving change still
 * counts exactly its opening hours.
 */
public final class BusinessCalendar {

    /** Opening hours of one weekday. */
    public record Window(LocalTime open, LocalTime close) {
        public Window {
            if (!open.isBefore(close)) {
                throw new IllegalArgumentException("opening time " + open + " must be before closing time " + close);
            }
        }
    }

    /** Never used by {@link #add} when a calendar is closed every day: the walk stops here. */
    static final int MAX_DAYS_TO_WALK = 366 * 10;

    private final ZoneId zone;
    private final Map<DayOfWeek, Window> hours;
    private final Set<LocalDate> holidays;
    private final boolean alwaysOpen;

    private BusinessCalendar(ZoneId zone, Map<DayOfWeek, Window> hours, Set<LocalDate> holidays, boolean alwaysOpen) {
        this.zone = zone;
        this.hours = Map.copyOf(hours);
        this.holidays = Set.copyOf(holidays);
        this.alwaysOpen = alwaysOpen;
    }

    public static BusinessCalendar of(ZoneId zone, Map<DayOfWeek, Window> hours, Set<LocalDate> holidays) {
        return new BusinessCalendar(zone, hours, holidays, false);
    }

    /** Every hour of every day counts; holidays are ignored. */
    public static BusinessCalendar roundTheClock() {
        return new BusinessCalendar(ZoneId.of("UTC"), Map.of(), Set.of(), true);
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean alwaysOpen() {
        return alwaysOpen;
    }

    public Map<DayOfWeek, Window> hours() {
        return hours;
    }

    public Set<LocalDate> holidays() {
        return holidays;
    }

    /** Business time between two instants; zero when {@code to} is not after {@code from}. */
    public Duration elapsed(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return Duration.ZERO;
        }
        if (alwaysOpen) {
            return Duration.between(from, to);
        }
        Duration total = Duration.ZERO;
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        while (!day.isAfter(last)) {
            Open open = window(day);
            if (open != null) {
                Instant start = open.start().isAfter(from) ? open.start() : from;
                Instant end = open.end().isBefore(to) ? open.end() : to;
                if (end.isAfter(start)) {
                    total = total.plus(Duration.between(start, end));
                }
            }
            day = day.plusDays(1);
        }
        return total;
    }

    /**
     * The instant at which {@code businessTime} has passed since {@code from}. Time outside opening
     * hours does not count, so the result always lies inside a window (or exactly at a closing
     * time, when the duration ends there). A zero duration from outside the hours lands at the
     * next opening.
     */
    public Instant add(Instant from, Duration businessTime) {
        if (businessTime.isNegative()) {
            throw new IllegalArgumentException("business time must not be negative: " + businessTime);
        }
        if (alwaysOpen) {
            return from.plus(businessTime);
        }
        Duration remaining = businessTime;
        LocalDate day = from.atZone(zone).toLocalDate();
        for (int walked = 0; walked < MAX_DAYS_TO_WALK; walked++, day = day.plusDays(1)) {
            Open open = window(day);
            if (open == null || !open.end().isAfter(from)) {
                continue;
            }
            Instant cursor = open.start().isAfter(from) ? open.start() : from;
            Duration available = Duration.between(cursor, open.end());
            if (remaining.compareTo(available) <= 0) {
                return cursor.plus(remaining);
            }
            remaining = remaining.minus(available);
        }
        throw new IllegalStateException("calendar has no opening hours in the next ten years");
    }

    /**
     * The same local time {@code days} open days later: closed days and holidays are skipped. Used for rules stated in
     * days ("closes three business days after resolution"); a round-the-clock calendar counts every day.
     */
    public Instant plusBusinessDays(Instant from, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must not be negative: " + days);
        }
        java.time.ZonedDateTime local = from.atZone(zone);
        LocalDate day = local.toLocalDate();
        int counted = 0;
        for (int walked = 0; counted < days; walked++) {
            if (walked >= MAX_DAYS_TO_WALK) {
                throw new IllegalStateException("calendar has no open days in the next ten years");
            }
            day = day.plusDays(1);
            if (alwaysOpen || window(day) != null) {
                counted++;
            }
        }
        return day.atTime(local.toLocalTime()).atZone(zone).toInstant();
    }

    public boolean isOpen(Instant at) {
        if (alwaysOpen) {
            return true;
        }
        Open open = window(at.atZone(zone).toLocalDate());
        return open != null && !at.isBefore(open.start()) && at.isBefore(open.end());
    }

    private record Open(Instant start, Instant end) {}

    /** The day's opening window as instants, or null when the calendar is closed that day. */
    private @Nullable Open window(LocalDate day) {
        Window w = hours.get(day.getDayOfWeek());
        if (w == null || holidays.contains(day)) {
            return null;
        }
        return new Open(
                day.atTime(w.open()).atZone(zone).toInstant(),
                day.atTime(w.close()).atZone(zone).toInstant());
    }
}
