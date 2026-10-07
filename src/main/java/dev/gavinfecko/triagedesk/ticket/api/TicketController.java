package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.common.web.PageResponse;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.NewTicket;
import dev.gavinfecko.triagedesk.ticket.application.TicketSummary;
import dev.gavinfecko.triagedesk.ticket.application.TicketView;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import dev.gavinfecko.triagedesk.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    public record CreateTicketRequest(
            @NotBlank @Size(min = 5, max = 120) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull UUID categoryId,
            @NotNull Priority priority,
            @Nullable UUID requesterId,
            @Nullable UUID queueId) {}

    @PostMapping
    @Operation(
            summary = "Open a ticket",
            description = "Agents and admins may set requester_id (a ticket phoned in) and queue_id. "
                    + "Requesters always open their own ticket in the category's queue; staff-only fields they "
                    + "send are ignored and listed in warnings.")
    public ResponseEntity<TicketView> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketView ticket = tickets.create(new NewTicket(
                request.title(),
                request.description(),
                request.categoryId(),
                request.priority(),
                request.requesterId(),
                request.queueId()));
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.key()))
                .body(ticket);
    }

    public record TransitionRequest(
            @NotNull TicketStatus to,
            @Nullable @Size(max = 10_000) String comment) {}

    @PostMapping("/{key}/transitions")
    @Operation(
            summary = "Move a ticket to another status",
            description = "NEW→OPEN (staff), OPEN→PENDING (staff, comment required), PENDING→OPEN (anyone), "
                    + "OPEN|PENDING→RESOLVED (staff, comment required), RESOLVED→CLOSED (requester or admin), "
                    + "RESOLVED→OPEN (anyone), NEW→CANCELLED (requester or admin), OPEN|PENDING→CANCELLED (admin). "
                    + "Anything else is 409 ticket-state-conflict. A comment is stored as a public reply.")
    public TicketView transition(@PathVariable String key, @Valid @RequestBody TransitionRequest request) {
        return tickets.transition(key, request.to(), request.comment());
    }

    @GetMapping
    @Operation(summary = "List tickets, newest first: your own as a requester, all of them as an agent or admin")
    public PageResponse<TicketSummary> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return tickets.list(page, size);
    }

    @GetMapping("/{key}")
    @Operation(summary = "One ticket by its key (HD-001234); someone else's ticket is a 404 for requesters")
    public TicketView get(@PathVariable String key) {
        return tickets.get(key);
    }
}
