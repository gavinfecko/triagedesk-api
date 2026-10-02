package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.ticket.application.ReferenceData;
import dev.gavinfecko.triagedesk.ticket.application.ReferenceData.CategoryView;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Ref;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference data")
public class ReferenceDataController {

    private final ReferenceData reference;

    public ReferenceDataController(ReferenceData reference) {
        this.reference = reference;
    }

    @GetMapping("/categories")
    @Operation(summary = "Ticket categories, each with the queue it routes to")
    public List<CategoryView> categories() {
        return reference.categories();
    }

    @GetMapping("/queues")
    @Operation(summary = "Work queues")
    public List<Ref> queues() {
        return reference.queues();
    }
}
