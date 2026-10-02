package dev.gavinfecko.triagedesk.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A work queue agents pick tickets from. Reference data (R__reference_data.sql). */
@Entity
@Table(name = "queues")
public class Queue {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected Queue() {}

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }
}
