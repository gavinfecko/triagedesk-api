package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.web.PageResponse;
import dev.gavinfecko.triagedesk.common.web.Preconditions;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.NewTicket;
import dev.gavinfecko.triagedesk.ticket.application.TicketService.TicketEdits;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
                    + "RESOLVED→OPEN (anyone), NEW→CANCELLED (requester or admin), OPEN|PENDING→CANCELLED (admin, comment required). "
                    + "Anything else is 409 ticket-state-conflict. A comment is stored as a public reply.")
    public TicketView transition(@PathVariable String key, @Valid @RequestBody TransitionRequest request) {
        return tickets.transition(key, request.to(), request.comment());
    }

    public record AssignRequest(@Nullable String assigneeId) {}

    public record QueueRequest(@NotNull UUID queueId) {}

    @PostMapping("/{key}/assign")
    @Operation(
            summary = "Assign a ticket (staff): \"me\", another agent's or admin's id, or null to unassign",
            description = "Taking a NEW ticket opens it.")
    public TicketView assign(@PathVariable String key, @RequestBody AssignRequest request) {
        return tickets.assign(key, request.assigneeId());
    }

    @PostMapping("/{key}/queue")
    @Operation(summary = "Move a ticket to another queue (staff)")
    public TicketView moveToQueue(@PathVariable String key, @Valid @RequestBody QueueRequest request) {
        return tickets.moveToQueue(key, request.queueId());
    }

    @GetMapping
    @Operation(summary = "List tickets, newest first: your own as a requester, all of them as an agent or admin")
    public PageResponse<TicketSummary> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return tickets.list(page, size);
    }

    @GetMapping("/{key}")
    @Operation(
            summary = "One ticket by its key (HD-001234); someone else's ticket is a 404 for requesters",
            description = "The response carries an ETag; send it back as If-Match when you PATCH.")
    public ResponseEntity<TicketView> get(@PathVariable String key) {
        TicketView view = tickets.get(key);
        return ResponseEntity.ok().eTag(Preconditions.etag(view.version())).body(view);
    }

    public record EditTicketRequest(
            @Nullable @Size(min = 5, max = 120) String title,
            @Nullable @Size(max = 5000) String description,
            @Nullable Priority priority,
            @Nullable UUID categoryId) {
        boolean isEmpty() {
            return title == null && description == null && priority == null && categoryId == null;
        }
    }

    @PatchMapping("/{key}")
    @Operation(
            summary = "Edit a ticket: title, description, priority, category",
            description =
                    "Requires If-Match with the ETag you last read: missing is 428, stale is 412. Staff may edit any open "
                            + "ticket; a requester may change the title and description of their own ticket only while it is NEW, "
                            + "and never the priority or category. Changing the category does not move the queue (see /queue).")
    public ResponseEntity<TicketView> update(
            @PathVariable String key,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @Valid @RequestBody EditTicketRequest request) {
        if (request.isEmpty()) {
            throw new InvalidFieldException("title", "send at least one of title, description, priority, category_id");
        }
        TicketView view = tickets.update(
                key,
                new TicketEdits(request.title(), request.description(), request.priority(), request.categoryId()),
                Preconditions.expectedVersion(ifMatch));
        return ResponseEntity.ok().eTag(Preconditions.etag(view.version())).body(view);
    }
}
