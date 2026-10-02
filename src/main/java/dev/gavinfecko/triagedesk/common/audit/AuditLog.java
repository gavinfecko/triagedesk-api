package dev.gavinfecko.triagedesk.common.audit;

import dev.gavinfecko.triagedesk.common.web.CorrelationIdFilter;
import java.sql.Timestamp;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events in the caller's transaction, so a change and its audit row commit or roll
 * back together. Append-only: there is deliberately no update or delete.
 */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public AuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public void record(AuditEvent event) {
        jdbc.sql("""
                        insert into audit_events
                          (action, actor_id, user_id, ticket_id, field, before_value, after_value, correlation_id, created_at)
                        values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                        """)
                .params(
                        event.action(),
                        event.actorId(),
                        event.userId(),
                        event.ticketId(),
                        event.field(),
                        toJson(event.before()),
                        toJson(event.after()),
                        CorrelationIdFilter.current(),
                        Timestamp.from(clock.instant()))
                .update();
    }

    private @Nullable String toJson(@Nullable Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}
