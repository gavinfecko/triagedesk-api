# Architecture Decision Records

One file per decision, numbered, never deleted. A superseded ADR stays and points at its replacement. Format: Status · Context · Decision · Consequences (MADR-lite). New ADRs are proposed in the PR that implements the decision.

| # | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-java-21-spring-boot-4.md) | Java 21 LTS and Spring Boot 4.1 | Accepted (versions confirmed in Sprint 0 by TD-9) |
| [0003](0003-maven-wrapper-build.md) | Maven with the wrapper, not Gradle | Accepted |
| [0004](0004-postgresql-flyway-jpa.md) | PostgreSQL, Flyway migrations, JPA with validate-only | Accepted |
| [0005](0005-modular-monolith-package-by-feature.md) | Modular monolith, package by feature, enforced by ArchUnit | Accepted |
| [0006](0006-stateless-jwt-auth.md) | Stateless JWT with rotating refresh tokens, not Keycloak | Accepted |
| [0007](0007-testcontainers-integration-tests.md) | Testcontainers PostgreSQL for every data and integration test | Accepted |
| [0008](0008-github-projects-as-agile-tooling.md) | GitHub Issues, Projects and Actions as the Agile toolchain | Accepted |
| [0009](0009-path-versioning.md) | Path versioning `/api/v1` | Accepted |
| [0010](0010-in-process-events-no-broker.md) | In-process domain events, no message broker in v1 | Accepted |
