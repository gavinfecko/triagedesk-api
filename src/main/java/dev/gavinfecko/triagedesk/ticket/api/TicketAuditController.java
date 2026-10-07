package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.ticket.application.AuditEntry;
import dev.gavinfecko.triagedesk.ticket.application.TicketAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{key}/audit")
@Tag(name = "Tickets")
public class TicketAuditController {

    private final TicketAuditService audit;

    public TicketAuditController(TicketAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(
            summary = "Who changed what on this ticket, and when, oldest first",
            description =
                    "Staff see every change. Requesters see creation, status, assignee, wording, priority, category and "
                            + "public replies; internal notes and routing stay internal.")
    public List<AuditEntry> trail(@PathVariable String key) {
        return audit.trail(key);
    }
}
