-- V5 (TD-20): queues, categories and tickets. Ticket keys come from a sequence: HD-001000, HD-001001…
CREATE TABLE queues (
    id         uuid        PRIMARY KEY,
    name       varchar(60) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT queues_name_unique UNIQUE (name)
);

CREATE TABLE categories (
    id               uuid        PRIMARY KEY,
    name             varchar(60) NOT NULL,
    default_queue_id uuid        NOT NULL REFERENCES queues (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT categories_name_unique UNIQUE (name)
);

CREATE SEQUENCE ticket_key_seq START WITH 1000;

CREATE TABLE tickets (
    id                 uuid          PRIMARY KEY,
    ticket_key         varchar(16)   NOT NULL,
    title              varchar(120)  NOT NULL,
    description        varchar(5000) NOT NULL,
    status             varchar(16)   NOT NULL,
    priority           varchar(16)   NOT NULL,
    category_id        uuid          NOT NULL REFERENCES categories (id),
    queue_id           uuid          NOT NULL REFERENCES queues (id),
    requester_id       uuid          NOT NULL REFERENCES users (id),
    assignee_id        uuid          REFERENCES users (id),
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    first_responded_at timestamptz,
    resolved_at        timestamptz,
    closed_at          timestamptz,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT tickets_key_unique UNIQUE (ticket_key),
    CONSTRAINT tickets_status_known CHECK (status IN ('NEW', 'OPEN', 'PENDING', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT tickets_priority_known CHECK (priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'))
);
CREATE INDEX tickets_status_priority_idx ON tickets (status, priority);
CREATE INDEX tickets_queue_status_idx ON tickets (queue_id, status);
CREATE INDEX tickets_assignee_idx ON tickets (assignee_id);
CREATE INDEX tickets_requester_created_idx ON tickets (requester_id, created_at DESC);
CREATE INDEX tickets_created_idx ON tickets (created_at DESC);

ALTER TABLE audit_events
    ADD CONSTRAINT audit_events_ticket_fk FOREIGN KEY (ticket_id) REFERENCES tickets (id);
