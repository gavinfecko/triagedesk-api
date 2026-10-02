-- V3 (TD-11): opaque refresh tokens, stored as SHA-256 hashes. A family is one login session;
-- rotation (TD-12) replaces the token inside its family, and reuse revokes the whole family.
CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    issued_at   timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
