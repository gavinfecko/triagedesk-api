package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.ticket.domain.Queue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueRepository extends JpaRepository<Queue, UUID> {}
