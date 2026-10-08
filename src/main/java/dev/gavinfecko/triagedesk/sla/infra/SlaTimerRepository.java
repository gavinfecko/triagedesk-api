package dev.gavinfecko.triagedesk.sla.infra;

import dev.gavinfecko.triagedesk.sla.domain.SlaTimer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaTimerRepository extends JpaRepository<SlaTimer, UUID> {

    List<SlaTimer> findByTicketIdOrderByStartedAtAsc(UUID ticketId);
}
