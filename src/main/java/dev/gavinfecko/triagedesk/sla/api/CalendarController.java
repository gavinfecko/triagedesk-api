package dev.gavinfecko.triagedesk.sla.api;

import dev.gavinfecko.triagedesk.sla.application.CalendarService;
import dev.gavinfecko.triagedesk.sla.application.CalendarService.CalendarChanges;
import dev.gavinfecko.triagedesk.sla.application.CalendarView;
import dev.gavinfecko.triagedesk.sla.application.CalendarView.Holiday;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sla/calendars")
@Tag(name = "SLA", description = "Policies and business-hours calendars (admin)")
public class CalendarController {

    private final CalendarService calendars;

    public CalendarController(CalendarService calendars) {
        this.calendars = calendars;
    }

    public record CalendarRequest(
            @NotBlank @Size(min = 2, max = 60) String name,
            @NotBlank String zone,
            @NotNull Map<String, List<String>> hours,
            @NotNull List<Holiday> holidays) {}

    @GetMapping
    @Operation(summary = "Every calendar")
    public List<CalendarView> list() {
        return calendars.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One calendar with its hours and holidays")
    public CalendarView get(@PathVariable UUID id) {
        return calendars.get(id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a calendar's name, zone, hours and holidays (admin)",
            description = "hours maps weekday (MON … SUN) to [open, close] as HH:mm; a missing weekday is closed. "
                    + "The 24x7 calendar cannot be edited.")
    public CalendarView update(@PathVariable UUID id, @Valid @RequestBody CalendarRequest request) {
        return calendars.update(
                id, new CalendarChanges(request.name(), request.zone(), request.hours(), request.holidays()));
    }
}
