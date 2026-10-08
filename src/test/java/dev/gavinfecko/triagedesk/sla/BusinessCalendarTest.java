package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar.Window;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Example-based checks of the business-time arithmetic; the laws are in BusinessCalendarPropertyTest. */
class BusinessCalendarTest {

    static final ZoneId NY = ZoneId.of("America/New_York");
    static final Window EIGHT_TO_SIX = new Window(LocalTime.of(8, 0), LocalTime.of(18, 0));

    static BusinessCalendar clinic(Set<LocalDate> holidays) {
        Map<DayOfWeek, Window> hours = Map.of(
                DayOfWeek.MONDAY, EIGHT_TO_SIX,
                DayOfWeek.TUESDAY, EIGHT_TO_SIX,
                DayOfWeek.WEDNESDAY, EIGHT_TO_SIX,
                DayOfWeek.THURSDAY, EIGHT_TO_SIX,
                DayOfWeek.FRIDAY, EIGHT_TO_SIX);
        return BusinessCalendar.of(NY, hours, holidays);
    }

    static Instant ny(String date, String time) {
        return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), NY)
                .toInstant();
    }

    @Test
    void withinOneDayOnlyOpeningHoursCount() {
        BusinessCalendar cal = clinic(Set.of());
        assertThat(cal.elapsed(ny("2026-10-07", "09:00"), ny("2026-10-07", "11:30")))
                .isEqualTo(Duration.ofMinutes(150));
        assertThat(cal.elapsed(ny("2026-10-07", "06:00"), ny("2026-10-07", "09:00")))
                .isEqualTo(Duration.ofHours(1));
        assertThat(cal.elapsed(ny("2026-10-07", "17:00"), ny("2026-10-07", "23:00")))
                .isEqualTo(Duration.ofHours(1));
        assertThat(cal.elapsed(ny("2026-10-07", "19:00"), ny("2026-10-07", "23:00")))
                .isZero();
        assertThat(cal.elapsed(ny("2026-10-07", "11:00"), ny("2026-10-07", "09:00")))
                .as("to before from")
                .isZero();
    }

    @Test
    void weekendsAndHolidaysContributeNothing() {
        BusinessCalendar cal = clinic(Set.of(LocalDate.parse("2026-10-12"))); // Columbus Day, a Monday
        assertThat(cal.elapsed(ny("2026-10-09", "17:00"), ny("2026-10-13", "09:00")))
                .isEqualTo(Duration.ofHours(2));
        assertThat(cal.elapsed(ny("2026-10-10", "00:00"), ny("2026-10-12", "23:59")))
                .isZero();
    }

    @Test
    void addLandsInsideOpeningHoursAndStartsAtTheNextOpeningWhenClosed() {
        BusinessCalendar cal = clinic(Set.of());
        assertThat(cal.add(ny("2026-10-07", "16:00"), Duration.ofHours(4))).isEqualTo(ny("2026-10-08", "10:00"));
        assertThat(cal.add(ny("2026-10-09", "17:30"), Duration.ofHours(1)))
                .as("Friday evening spills into Monday")
                .isEqualTo(ny("2026-10-12", "08:30"));
        assertThat(cal.add(ny("2026-10-10", "12:00"), Duration.ZERO))
                .as("zero from a Saturday is Monday's opening")
                .isEqualTo(ny("2026-10-12", "08:00"));
        assertThat(cal.add(ny("2026-10-07", "17:00"), Duration.ofHours(1)))
                .as("ending exactly at close")
                .isEqualTo(ny("2026-10-07", "18:00"));
        assertThat(cal.add(ny("2026-10-07", "09:00"), Duration.ofHours(30)))
                .as("three full days")
                .isEqualTo(ny("2026-10-12", "09:00"));
    }

    @Test
    void daylightSavingChangesStillCountTheOpeningHours() {
        BusinessCalendar cal = clinic(Set.of());
        assertThat(cal.elapsed(ny("2026-03-09", "08:00"), ny("2026-03-09", "18:00")))
                .isEqualTo(Duration.ofHours(10));
        assertThat(cal.elapsed(ny("2026-10-30", "08:00"), ny("2026-11-02", "18:00")))
                .isEqualTo(Duration.ofHours(20));
        assertThat(cal.add(ny("2026-10-30", "17:00"), Duration.ofHours(2))).isEqualTo(ny("2026-11-02", "09:00"));
    }

    @Test
    void alwaysOpenCountsWallClockTime() {
        BusinessCalendar cal = BusinessCalendar.roundTheClock();
        Instant from = Instant.parse("2026-10-10T22:00:00Z");
        assertThat(cal.elapsed(from, from.plus(Duration.ofHours(30)))).isEqualTo(Duration.ofHours(30));
        assertThat(cal.add(from, Duration.ofMinutes(15))).isEqualTo(from.plus(Duration.ofMinutes(15)));
        assertThat(cal.isOpen(from)).isTrue();
    }

    @Test
    void isOpenAndGuards() {
        BusinessCalendar cal = clinic(Set.of());
        assertThat(cal.isOpen(ny("2026-10-07", "08:00"))).isTrue();
        assertThat(cal.isOpen(ny("2026-10-07", "18:00")))
                .as("closing time is closed")
                .isFalse();
        assertThat(cal.isOpen(ny("2026-10-11", "12:00"))).isFalse();
        assertThatThrownBy(() -> new Window(LocalTime.of(18, 0), LocalTime.of(8, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cal.add(ny("2026-10-07", "08:00"), Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        BusinessCalendar closed = BusinessCalendar.of(NY, Map.of(), Set.of());
        assertThatThrownBy(() -> closed.add(ny("2026-10-07", "08:00"), Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
