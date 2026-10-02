-- V2 (TD-10): user accounts and the audit trail every later story writes to.
CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(320) NOT NULL,
    display_name  varchar(80)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_lower CHECK (email = lower(email)),
    CONSTRAINT users_role_known CHECK (role IN ('ADMIN', 'AGENT', 'REQUESTER'))
);
CREATE INDEX users_role_active_idx ON users (role, active);

-- Append-only. Who did what, to which user or ticket, with before/after values (TD-31 reads it).
CREATE TABLE audit_events (
    id             bigserial    PRIMARY KEY,
    action         varchar(64)  NOT NULL,
    actor_id       uuid,                      -- null means the system did it
    user_id        uuid,
    ticket_id      uuid,                      -- foreign key added with the tickets table
    field          varchar(64),
    before_value   jsonb,
    after_value    jsonb,
    correlation_id varchar(64),
    created_at     timestamptz  NOT NULL
);
CREATE INDEX audit_events_user_idx ON audit_events (user_id, created_at);
CREATE INDEX audit_events_ticket_idx ON audit_events (ticket_id, created_at);
