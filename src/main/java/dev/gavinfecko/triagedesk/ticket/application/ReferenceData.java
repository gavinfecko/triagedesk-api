package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.ticket.application.TicketView.Ref;
import dev.gavinfecko.triagedesk.ticket.infra.CategoryRepository;
import dev.gavinfecko.triagedesk.ticket.infra.QueueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories and queues, so a client can build the "new ticket" form without hard-coding ids. */
@Service
@Transactional(readOnly = true)
public class ReferenceData {

    private final CategoryRepository categories;
    private final QueueRepository queues;

    public ReferenceData(CategoryRepository categories, QueueRepository queues) {
        this.categories = categories;
        this.queues = queues;
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
