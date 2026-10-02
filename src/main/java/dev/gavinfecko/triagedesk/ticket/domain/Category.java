package dev.gavinfecko.triagedesk.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** What kind of problem a ticket is about; decides the queue it lands in unless an agent says otherwise. */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "default_queue_id", nullable = false)
    private UUID defaultQueueId;

    protected Category() {}

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID defaultQueueId() {
        return defaultQueueId;
    }
}
