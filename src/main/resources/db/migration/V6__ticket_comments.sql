-- V6 (TD-30): the conversation on a ticket. PUBLIC replies are visible to the requester;
-- INTERNAL notes are for agents and admins only, enforced in the query, never only in the mapper.
CREATE TABLE ticket_comments (
    id         uuid           PRIMARY KEY,
    ticket_id  uuid           NOT NULL REFERENCES tickets (id),
    author_id  uuid           NOT NULL REFERENCES users (id),
    visibility varchar(8)     NOT NULL,
    body       varchar(10000) NOT NULL,
    created_at timestamptz    NOT NULL,
    CONSTRAINT ticket_comments_visibility_known CHECK (visibility IN ('PUBLIC', 'INTERNAL'))
);
CREATE INDEX ticket_comments_ticket_idx ON ticket_comments (ticket_id, created_at);
