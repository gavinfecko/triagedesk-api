package dev.gavinfecko.triagedesk.sla.infra;

import dev.gavinfecko.triagedesk.sla.domain.CalendarDefinition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarRepository extends JpaRepository<CalendarDefinition, UUID> {}
