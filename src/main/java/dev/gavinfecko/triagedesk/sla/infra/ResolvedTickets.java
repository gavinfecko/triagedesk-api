package dev.gavinfecko.triagedesk.sla.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Resolved tickets old enough to be auto-close candidates, with the policy their resolution clock ran on. Reads the
 * ticket table directly: the SLA module and the ticket module share one database, and this is a read.
 */
@Component
public class ResolvedTickets {

    public record Candidate(
            UUID ticketId, Instant resolvedAt, @Nullable String policySnapshot) {}

    private final JdbcClient jdbc;

    public ResolvedTickets(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** RESOLVED before {@code resolvedBefore}, oldest first. */
    public List<Candidate> resolvedBefore(Instant resolvedBefore, int limit) {
        return jdbc.sql("""
                        select t.id, t.resolved_at,
                               (select s.policy_snapshot::text from sla_timers s
                                where s.ticket_id = t.id and s.kind = 'RESOLUTION'
                                order by s.started_at desc limit 1) as policy_snapshot
                        from tickets t
                        where t.status = 'RESOLVED' and t.resolved_at < ?
                        order by t.resolved_at
                        limit ?""")
                .params(Timestamp.from(resolvedBefore), limit)
                .query((rs, n) -> new Candidate(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("resolved_at").toInstant(),
                        rs.getString("policy_snapshot")))
                .list();
    }
}
