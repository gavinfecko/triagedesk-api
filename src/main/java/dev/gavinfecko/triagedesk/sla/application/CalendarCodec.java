package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar.Window;
import dev.gavinfecko.triagedesk.sla.domain.CalendarDefinition;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Translates between the API/storage form ({@code {"MON": ["08:00","18:00"]}}) and {@link BusinessCalendar}. */
@Component
public class CalendarCodec {

    private static final TypeReference<Map<String, List<String>>> HOURS = new TypeReference<>() {};

    private final JsonMapper json;

    public CalendarCodec(JsonMapper json) {
        this.json = json;
    }

    public BusinessCalendar toCalendar(CalendarDefinition definition) {
        if (definition.alwaysOpen()) {
            return BusinessCalendar.roundTheClock();
        }
        return BusinessCalendar.of(
                ZoneId.of(definition.zone()),
                parseHours(json.readValue(definition.hours(), HOURS)),
                definition.holidays().stream()
                        .map(CalendarDefinition.HolidayEntry::date)
                        .collect(Collectors.toSet()));
    }

    /** Validates and normalises an hours map; a bad entry is a validation problem naming {@code hours}. */
    public Map<DayOfWeek, Window> parseHours(Map<String, List<String>> raw) {
        Map<DayOfWeek, Window> hours = new EnumMap<>(DayOfWeek.class);
        for (Map.Entry<String, List<String>> e : raw.entrySet()) {
            DayOfWeek day = dayOf(e.getKey());
            List<String> times = e.getValue();
            if (times == null || times.size() != 2) {
                throw new InvalidFieldException("hours", e.getKey() + " must be [\"HH:mm\", \"HH:mm\"]");
            }
            try {
                hours.put(day, new Window(LocalTime.parse(times.get(0)), LocalTime.parse(times.get(1))));
            } catch (DateTimeParseException | IllegalArgumentException ex) {
                throw new InvalidFieldException("hours", e.getKey() + ": " + ex.getMessage());
            }
        }
        return hours;
    }

    public String toJson(Map<DayOfWeek, Window> hours) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            Window w = hours.get(day);
            if (w != null) {
                out.put(
                        day.name().substring(0, 3),
                        List.of(w.open().toString(), w.close().toString()));
            }
        }
        return json.writeValueAsString(out);
    }

    public Map<String, List<String>> toApi(String hoursJson) {
        return json.readValue(hoursJson, HOURS);
    }

    public static ZoneId zoneOf(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new InvalidFieldException("zone", "'" + zone + "' is not a time zone id (e.g. America/New_York)");
        }
    }

    private static DayOfWeek dayOf(String key) {
        String k = key.strip().toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equals(k) || day.name().startsWith(k) && k.length() == 3) {
                return day;
            }
        }
        throw new InvalidFieldException("hours", "'" + key + "' is not a weekday (use MON … SUN)");
    }

    static Set<DayOfWeek> weekdays() {
        return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }
}
