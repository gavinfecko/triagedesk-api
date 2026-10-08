package dev.gavinfecko.triagedesk.sla.infra;

import dev.gavinfecko.triagedesk.sla.domain.SlaPolicy;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    Optional<SlaPolicy> findByPriority(Priority priority);
}
