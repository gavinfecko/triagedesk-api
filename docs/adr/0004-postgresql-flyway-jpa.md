# ADR-0004: PostgreSQL, Flyway migrations, JPA with validate-only

**Status:** Accepted · **Date:** 2026-09-24

## Context
The system is relational (tickets, users, comments, timers, audit). Reporting needs real SQL (aggregates, date buckets). Search needs full-text. The schema must be versioned and reviewable.

## Decision
- **PostgreSQL 16** everywhere (Compose locally, Testcontainers in tests, managed instance in prod). No H2, ever: tests run against the real engine.
- **Flyway** owns the schema: `V{n}__` versioned SQL per story, `R__` repeatable for reference data. `spring.jpa.hibernate.ddl-auto=validate` so the entities can never drift from the migrations.
- **Spring Data JPA** for aggregates and simple queries; **native SQL** (via `JdbcClient`) for reporting and for anything where the generated SQL would be worse.
- All timestamps `timestamptz`, connection timezone UTC, `Clock` injected.

## Consequences
- Every schema change is a reviewed file in the PR.
- Reporting code is plain SQL a DBA could read and tune.
- Testcontainers requires Docker in CI and locally (see ADR-0007).
