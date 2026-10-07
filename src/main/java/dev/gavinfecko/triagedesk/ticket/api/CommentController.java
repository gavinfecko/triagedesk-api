package dev.gavinfecko.triagedesk.ticket.api;

import dev.gavinfecko.triagedesk.ticket.application.CommentService;
import dev.gavinfecko.triagedesk.ticket.application.CommentView;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{key}/comments")
@Tag(name = "Tickets")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    public record NewComment(
            @NotBlank @Size(max = 10_000) String body,
            @NotNull Visibility visibility) {}

    @GetMapping
    @Operation(summary = "The conversation, oldest first; requesters see PUBLIC comments only")
    public List<CommentView> list(@PathVariable String key) {
        return comments.list(key);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Reply (PUBLIC) or leave an internal note (INTERNAL, staff only)",
            description =
                    "Staff's first PUBLIC reply records the first response. A requester's PUBLIC reply on a PENDING "
                            + "ticket returns it to OPEN.")
    public CommentView add(@PathVariable String key, @Valid @RequestBody NewComment request) {
        return comments.add(key, request.visibility(), request.body());
    }
}
