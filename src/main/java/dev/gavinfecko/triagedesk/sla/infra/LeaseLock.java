package dev.gavinfecko.triagedesk.sla.infra;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A row lease in {@code sla_scan_lock}: one conditional UPDATE, committed on its own, decides which instance runs a
 * job. Postgres serialises concurrent updates of the row, so exactly one caller sees an expired lease.
 */
@Component
public class LeaseLock {

    private final JdbcClient jdbc;
    private final Clock clock;

    public LeaseLock(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean tryAcquire(String job, String owner, Duration lease) {
        Instant now = clock.instant();
        return jdbc.sql("update sla_scan_lock set owner = ?, locked_until = ? where name = ? and locked_until <= ?")
                        .params(owner, Timestamp.from(now.plus(lease)), job, Timestamp.from(now))
                        .update()
                == 1;
    }

    public void release(String job, String owner) {
        jdbc.sql("update sla_scan_lock set locked_until = ? where name = ? and owner = ?")
                .params(Timestamp.from(clock.instant()), job, owner)
                .update();
    }
}
