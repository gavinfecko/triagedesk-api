# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/). Each sprint ends with a release; the GitHub Release notes are the source and this file mirrors them.

## [Unreleased]

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
