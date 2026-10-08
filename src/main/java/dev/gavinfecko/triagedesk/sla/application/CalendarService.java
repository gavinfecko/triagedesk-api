package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.sla.application.CalendarView.Holiday;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar;
import dev.gavinfecko.triagedesk.sla.domain.BusinessCalendar.Window;
import dev.gavinfecko.triagedesk.sla.domain.CalendarDefinition;
import dev.gavinfecko.triagedesk.sla.domain.CalendarDefinition.HolidayEntry;
import dev.gavinfecko.triagedesk.sla.infra.CalendarRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendars for the SLA engine. Reading is for anyone who can see tickets; editing is for admins. */
@Service
public class CalendarService {

    private final CalendarRepository calendars;
    private final CalendarCodec codec;
    private final AuditLog audit;
    private final Clock clock;

    public CalendarService(CalendarRepository calendars, CalendarCodec codec, AuditLog audit, Clock clock) {
        this.calendars = calendars;
        this.codec = codec;
        this.audit = audit;
        this.clock = clock;
    }

    public record CalendarChanges(String name, String zone, Map<String, List<String>> hours, List<Holiday> holidays) {}

    @Transactional(readOnly = true)
    public List<CalendarView> list() {
        return calendars.findAll(Sort.by("name")).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public CalendarView get(UUID id) {
        return view(find(id));
    }

    /** The arithmetic form of a stored calendar, for the SLA engine. */
    @Transactional(readOnly = true)
    public BusinessCalendar calendar(UUID id) {
        return codec.toCalendar(find(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CalendarView update(UUID id, CalendarChanges changes) {
        CalendarDefinition definition = find(id);
        if (definition.alwaysOpen()) {
            throw new InvalidFieldException("hours", "the 24x7 calendar has no hours to edit");
        }
        CalendarCodec.zoneOf(changes.zone());
        Map<DayOfWeek, Window> hours = codec.parseHours(changes.hours());
        if (hours.isEmpty()) {
            throw new InvalidFieldException("hours", "at least one weekday must have opening hours");
        }
        Set<HolidayEntry> holidays = new HashSet<>();
        for (Holiday h : changes.holidays()) {
            if (h.date() == null || h.name() == null || h.name().isBlank()) {
                throw new InvalidFieldException("holidays", "every holiday needs a date and a name");
            }
            holidays.add(new HolidayEntry(h.date(), h.name().strip()));
        }
        CalendarView before = view(definition);
        definition.update(changes.name(), changes.zone(), codec.toJson(hours), holidays, clock.instant());
        CalendarView after = view(definition);
        audit.record(AuditEvent.of("sla.calendar_changed")
                .actor(CurrentUser.get().id())
                .change("calendar:" + id, before, after));
        return after;
    }

    private CalendarDefinition find(UUID id) {
        return calendars.findById(id).orElseThrow(() -> new NotFoundException("Calendar", id));
    }

    private CalendarView view(CalendarDefinition d) {
        List<Holiday> holidays = d.holidays().stream()
                .map(h -> new Holiday(h.date(), h.name()))
                .sorted(Comparator.comparing(Holiday::date))
                .collect(Collectors.toList());
        return new CalendarView(
                d.id(), d.name(), d.zone(), d.alwaysOpen(), codec.toApi(d.hours()), holidays, d.updatedAt());
    }
}
