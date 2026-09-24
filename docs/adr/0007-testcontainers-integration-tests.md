# ADR-0007: Testcontainers PostgreSQL for every data and integration test

**Status:** Accepted · **Date:** 2026-09-24

## Context
Tests against an in-memory database pass and then production fails on a PostgreSQL-specific type, index or function (`tsvector`, `jsonb`, partial indexes, `citext`). The project uses all of those.

## Decision
`@DataJpaTest` and `@SpringBootTest` tests run against a real PostgreSQL 16 container started once per JVM by Testcontainers and reused across test classes. Migrations run on it from empty, so every test run is also a migration test. Pure domain logic (state machine, `BusinessCalendar`) is tested without Spring or a database.

## Consequences
- Docker must be running locally (`make up` starts Colima) and is available in GitHub Actions by default.
- Integration suite is slower than in-memory; mitigated by container reuse and the Surefire/Failsafe split.
