package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.support.ApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * With ten thousand tickets the list queries must hit the indexes from ARCHITECTURE.md §8. The rows
 * are inserted with one statement and keyed {@code IX-…} so they cannot collide with real tickets.
 */
@ApiTest
class TicketIndexTest {

    @Autowired
    JdbcClient jdbc;

    void tenThousandTickets() {
        UUID requester = UUID.randomUUID();
        jdbc.sql("""
                        insert into users (id, email, display_name, password_hash, role, active, created_at, updated_at)
                        values (?, ?, 'Index Fixture', 'x', 'REQUESTER', true, now(), now())""").params(requester, "index-" + requester + "@clinic.test").update();
        jdbc.sql("""
                        insert into tickets (id, ticket_key, title, description, status, priority, category_id, queue_id,
                                             requester_id, created_at, updated_at)
                        select gen_random_uuid(), 'IX-' || lpad(g::text, 6, '0'), 'Index fixture ' || g, 'generated',
                               (array['NEW','OPEN','PENDING','RESOLVED','CLOSED'])[1 + g % 5],
                               case when g % 20 = 0 then 'P1_CRITICAL' when g % 5 = 0 then 'P2_HIGH' when g % 2 = 0 then 'P3_MEDIUM' else 'P4_LOW' end,
                               '00000000-0000-4000-8000-000000000201',
                               (array['00000000-0000-4000-8000-000000000101','00000000-0000-4000-8000-000000000102',
                                      '00000000-0000-4000-8000-000000000103','00000000-0000-4000-8000-000000000104'])[1 + g % 4]::uuid,
                               ?, now() - (g || ' minutes')::interval, now() - (g || ' minutes')::interval
                        from generate_series(1, 10000) g
                        on conflict (ticket_key) do nothing""").param(requester).update();
        jdbc.sql("analyze tickets").update();
    }

    String plan(String sql) {
        List<String> lines = jdbc.sql("explain " + sql).query(String.class).list();
        return String.join("\n", lines);
    }

    /** The list endpoint runs two queries per page: a count over the filter, and the page itself. */
    @Test
    void theStatusAndPriorityFilterUsesItsIndex() {
        tenThousandTickets();
        String where = "status in ('OPEN','PENDING') and priority = 'P1_CRITICAL'";
        assertThat(plan("select count(*) from tickets where " + where)).contains("tickets_status_priority_idx");
        String page = plan("select * from tickets where " + where + " order by created_at desc limit 50");
        assertThat(page).as(page).doesNotContain("Seq Scan").contains("Index");
    }

    @Test
    void theQueueAndStatusFilterUsesItsIndex() {
        tenThousandTickets();
        String where = "queue_id = '00000000-0000-4000-8000-000000000103' and status = 'PENDING'";
        assertThat(plan("select count(*) from tickets where " + where)).contains("tickets_queue_status_idx");
        String page = plan("select * from tickets where " + where + " order by created_at desc limit 50");
        assertThat(page).as(page).doesNotContain("Seq Scan").contains("Index");
    }

    @Test
    void fullTextSearchUsesTheGinIndexThroughTheInlinedFunction() {
        tenThousandTickets();
        String plan = plan("select * from tickets where ticket_matches(search_vector, 'zebra') limit 50");
        assertThat(plan).as(plan).contains("tickets_search_idx");
    }

    @Test
    void aRequestersOwnListUsesTheRequesterIndex() {
        tenThousandTickets();
        String plan = plan("select * from tickets where requester_id = '" + UUID.randomUUID()
                + "' order by created_at desc limit 25");
        assertThat(plan).as(plan).contains("tickets_requester_created_idx");
    }
}
