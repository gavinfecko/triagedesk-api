# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/). Each sprint ends with a release; the GitHub Release notes are the source and this file mirrors them.

## [Unreleased]

## [0.2.0] — 2026-10-02
Sprint 1: users can register, log in and open a ticket they can read back, with roles enforced and a demo clinic to explore.

### Added
- Self-service registration as a requester with a breached-password check; emails stored lower-case; every registration audited (TD-10).
- Login with JWT access tokens (15 min, HS256 in dev, RS256 in production) and opaque refresh tokens (7 days, stored hashed); `GET /users/me`; failed logins and rejected tokens counted in `auth.login.failed` (TD-11).
- Refresh-token rotation; replaying a rotated token revokes the whole session family and is audited; logout (TD-12).
- Admin-only user administration on the URL and in the service; an executable role matrix over every endpoint (TD-13).
- Admin user management: paged list with filters, create any role with a temporary password, rename and change role with audit, deactivate (ends sessions), last-admin guard (TD-14).
- Tickets: create with `HD-######` keys, routing by category, staff filing on someone's behalf, warnings for ignored fields, audit row and `TicketCreated` event; categories and queues as reference data (TD-20).
- My tickets newest first and ticket detail; someone else's ticket is a 404 for requesters, enforced in the service (TD-21).
- Demo clinic: 9 users and 60 tickets across every status, seeded in dev and by `make seed` (TD-15).
- API JSON is snake_case, and the OpenAPI document matches it.

### Changed
- HTTP Basic is gone; bearer tokens are the only scheme.

## [0.1.0] — 2026-09-24
Sprint 0: the foundation. Nothing user-facing yet; everything later sprints build on.

### Added
- Spring Boot 4.1 / Java 21 skeleton with a stateless security chain; health probes public, everything else authenticated (TD-1).
- Docker Compose for PostgreSQL 16 and Mailpit with health checks; Flyway `V1__baseline`; UTC on every connection (TD-2).
- GitHub Actions: `lint`, `build-test` (Testcontainers), `security` (gitleaks, Trivy), `openapi-diff`, and a Docker image pushed to GHCR from `main` and tags (TD-3).
- RFC 9457 Problem Details for every error with stable `type` slugs, per-field validation errors and a correlation id in header, body and logs (TD-5).
- OpenAPI 3.1 document and Swagger UI; `ProblemDetail` schema and common error responses on every operation; `docs/openapi.yaml` regenerated on every build and diffed in CI (TD-6).
- Actuator `info` (build + git), `metrics` and `prometheus` for admins; one access-log line per request; `user_id` in the MDC; JSON logs in dev and prod (TD-7).
- ArchUnit module rules, Spotless formatting and a JaCoCo gate (80 % line / 70 % branch) enforced in `verify`; injected `Clock` (TD-8).
- Project plan, architecture, Agile process, backlog of 68 stories, ten ADRs, issue and PR templates, bootstrap scripts (TD-4, TD-9).

### Fixed
- Constraint violations on request parameters now return the validation shape with `errors[]` (#76).
