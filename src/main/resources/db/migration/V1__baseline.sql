-- V1: baseline. Extensions the schema relies on and a UTC default for this database.
-- Every later migration is one story's change (see docs/ARCHITECTURE.md §8).
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram search on titles (TD-33)
CREATE EXTENSION IF NOT EXISTS citext;    -- case-insensitive emails (TD-10)

DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO ''UTC''', current_database());
END $$;
