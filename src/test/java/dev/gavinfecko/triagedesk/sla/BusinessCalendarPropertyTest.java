package dev.gavinfecko.triagedesk.sla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar.Window;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** The laws every calendar must obey, checked over thousands of random instants and durations. */
class BusinessCalendarPropertyTest {

    static final ZoneId NY = ZoneId.of("America/New_York");
    static final Window EIGHT_TO_SIX = new Window(LocalTime.of(8, 0), LocalTime.of(18, 0));
    static final BusinessCalendar CAL = BusinessCalendar.of(
            NY,
            Map.of(
                    DayOfWeek.MONDAY, EIGHT_TO_SIX,
                    DayOfWeek.TUESDAY, EIGHT_TO_SIX,
                    DayOfWeek.WEDNESDAY, EIGHT_TO_SIX,
                    DayOfWeek.THURSDAY, EIGHT_TO_SIX,
                    DayOfWeek.FRIDAY, EIGHT_TO_SIX),
            Set.of(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 25), LocalDate.of(2026, 7, 3)));

    @Provide
    Arbitrary<Instant> instantsIn2026() {
        return Arbitraries.longs()
                .between(
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        Instant.parse("2026-12-31T00:00:00Z").getEpochSecond())
                .map(Instant::ofEpochSecond);
    }

    @Provide
    Arbitrary<Duration> businessDurations() {
        return Arbitraries.longs().between(0, 100 * 3600).map(Duration::ofSeconds);
    }

    @Property(tries = 2000)
    void addThenElapsedGivesTheDurationBack(
            @ForAll("instantsIn2026") Instant from, @ForAll("businessDurations") Duration d) {
        Instant to = CAL.add(from, d);
        assertThat(CAL.elapsed(from, to)).isEqualTo(d);
    }

    @Property(tries = 2000)
    void elapsedThenAddReturnsToTheSameInstantWhenItIsInsideOpeningHours(
            @ForAll("instantsIn2026") Instant from, @ForAll("instantsIn2026") Instant to) {
        if (!to.isAfter(from) || !CAL.isOpen(to)) {
            return;
        }
        assertThat(CAL.add(from, CAL.elapsed(from, to))).isEqualTo(to);
    }

    @Property(tries = 2000)
    void elapsedIsMonotonicAndNeverExceedsWallClockTime(
            @ForAll("instantsIn2026") Instant from,
            @ForAll("instantsIn2026") Instant t1,
            @ForAll("instantsIn2026") Instant t2) {
        Instant earlier = t1.isBefore(t2) ? t1 : t2;
        Instant later = t1.isBefore(t2) ? t2 : t1;
        assertThat(CAL.elapsed(from, earlier)).isLessThanOrEqualTo(CAL.elapsed(from, later));
        if (later.isAfter(from)) {
            assertThat(CAL.elapsed(from, later)).isLessThanOrEqualTo(Duration.between(from, later));
        }
    }

    @Property(tries = 500)
    void holidaysAndWeekendsContributeZero(@ForAll("instantsIn2026") Instant any) {
        LocalDate day = any.atZone(NY).toLocalDate();
        boolean closedDay = CAL.holidays().contains(day) || !CAL.hours().containsKey(day.getDayOfWeek());
        Instant startOfDay = day.atStartOfDay(NY).toInstant();
        Instant endOfDay = day.plusDays(1).atStartOfDay(NY).toInstant();
        if (closedDay) {
            assertThat(CAL.elapsed(startOfDay, endOfDay)).isZero();
        } else {
            assertThat(CAL.elapsed(startOfDay, endOfDay)).isEqualTo(Duration.ofHours(10));
        }
    }

    @Property(tries = 1000)
    void addAlwaysLandsOnAnOpenInstantOrExactlyAtAClose(
            @ForAll("instantsIn2026") Instant from, @ForAll("businessDurations") Duration d) {
        Instant to = CAL.add(from, d);
        boolean atClose = !CAL.isOpen(to) && CAL.isOpen(to.minusSeconds(1));
        assertThat(CAL.isOpen(to) || atClose).as("%s", to).isTrue();
    }
}
