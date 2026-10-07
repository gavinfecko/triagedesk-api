package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.ticket.domain.Comment;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<Comment> findByTicketIdAndVisibilityOrderByCreatedAtAsc(UUID ticketId, Visibility visibility);
}
