package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.NewTicket;
import dev.gavinfecko.triagedesk.ticket.application.TicketView;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
