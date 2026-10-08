package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.ticket.application.TicketView.Ref;
import dev.gavinfecko.triagedesk.ticket.infra.CategoryRepository;
import dev.gavinfecko.triagedesk.ticket.infra.QueueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories and queues, so a client can build the "new ticket" form without hard-coding ids. */
@Service
@Transactional(readOnly = true)
public class ReferenceData {

    private final CategoryRepository categories;
    private final QueueRepository queues;
    private final JdbcClient jdbc;

    public ReferenceData(CategoryRepository categories, QueueRepository queues, JdbcClient jdbc) {
        this.categories = categories;
        this.queues = queues;
        this.jdbc = jdbc;
    }

    public record TagView(UUID id, String name, long count) {}

    /** Every tag with how many tickets carry it, most used first. */
    public List<TagView> tags() {
        return jdbc.sql("""
                        select t.id, t.name, count(tt.ticket_id) as n from tags t
                        left join ticket_tags tt on tt.tag_id = t.id
                        group by t.id, t.name order by n desc, t.name""")
                .query((rs, i) -> new TagView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("n")))
                .list();
    }

    public record CategoryView(UUID id, String name, UUID defaultQueueId) {}

    public List<CategoryView> categories() {
        return categories.findAll(Sort.by("name")).stream()
                .map(c -> new CategoryView(c.id(), c.name(), c.defaultQueueId()))
                .toList();
    }

    public List<Ref> queues() {
        return queues.findAll(Sort.by("name")).stream()
                .map(q -> new Ref(q.id(), q.name()))
                .toList();
    }
}
