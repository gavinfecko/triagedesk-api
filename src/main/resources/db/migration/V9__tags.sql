-- V9 (TD-32): free-form tags agents put on tickets ("recurring", "vendor", "phishing").
CREATE TABLE tags (
    id         uuid        PRIMARY KEY,
    name       varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tags_name_unique UNIQUE (name),
    CONSTRAINT tags_name_normalized CHECK (name = lower(name) AND name ~ '^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$')
);

CREATE TABLE ticket_tags (
    ticket_id uuid NOT NULL REFERENCES tickets (id),
    tag_id    uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (ticket_id, tag_id)
);
CREATE INDEX ticket_tags_tag_idx ON ticket_tags (tag_id);
