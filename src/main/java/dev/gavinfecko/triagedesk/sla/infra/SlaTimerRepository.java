package dev.gavinfecko.triagedesk.sla.infra;

import dev.gavinfecko.triagedesk.sla.domain.SlaTimer;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlaTimerRepository extends JpaRepository<SlaTimer, UUID> {

    List<SlaTimer> findByTicketIdOrderByStartedAtAsc(UUID ticketId);

    List<SlaTimer> findByTicketIdIn(Collection<UUID> ticketIds);

    /**
     * Running clocks past their due instant, oldest first, locked for this transaction. Reads only the partial
     * index on running clocks; SKIP LOCKED lets a second scanner (if the lease ever failed) take other rows.
     */
    @Query(value = """
                    select * from sla_timers
                    where due_at < :now and met_at is null and breached_at is null
                      and cancelled_at is null and paused_at is null
                    order by due_at
                    limit :limit
                    for update skip locked""", nativeQuery = true)
    List<SlaTimer> lockOverdue(Instant now, int limit);
}
