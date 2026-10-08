package dev.gavinfecko.triagedesk.sla.api;

import dev.gavinfecko.triagedesk.sla.application.SlaTimerService;
import dev.gavinfecko.triagedesk.sla.application.TicketSlaView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{key}/sla")
@Tag(name = "SLA", description = "Policies and business-hours calendars (admin)")
public class TicketSlaController {

    private final SlaTimerService timers;

    public TicketSlaController(SlaTimerService timers) {
        this.timers = timers;
    }

    @GetMapping
    @Operation(
            summary = "The ticket's SLA clocks: first response and resolution",
            description = "status is on_track (under 75 % of the budget used), at_risk, breached, met, paused or "
                    + "cancelled; remaining_business_minutes counts only the policy calendar's opening hours. "
                    + "Requesters see their own tickets' clocks only (404 otherwise).")
    public TicketSlaView get(@PathVariable String key) {
        return timers.forTicket(key);
    }
}
