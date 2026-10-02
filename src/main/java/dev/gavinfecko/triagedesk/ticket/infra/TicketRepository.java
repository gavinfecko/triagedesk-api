package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByKey(String key);

    Page<Ticket> findByRequesterId(UUID requesterId, Pageable pageable);
}
