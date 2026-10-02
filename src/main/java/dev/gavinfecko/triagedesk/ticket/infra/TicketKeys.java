package dev.gavinfecko.triagedesk.ticket.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Human-readable ticket keys from a database sequence: {@code HD-001000}. Gaps are fine; duplicates are impossible. */
@Component
public class TicketKeys {

    private final JdbcClient jdbc;

    public TicketKeys(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        long value =
                jdbc.sql("select nextval('ticket_key_seq')").query(Long.class).single();
        return format(value);
    }

    public static String format(long sequenceValue) {
        return "HD-%06d".formatted(sequenceValue);
    }
}
