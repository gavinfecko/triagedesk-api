# TriageDesk API

> A help desk ticketing system for a small IT team, built in **Java 21 + Spring Boot 4** on **PostgreSQL**, and run as a solo **Agile** project with the whole process visible on GitHub: user stories on a board, one-week sprints, every change through a reviewed pull request with CI, a tagged release with notes each sprint.

Staff open tickets, agents work them from queues, SLA timers count down in **business hours** and pause while waiting on the requester, breaches escalate, every change is audited, and an admin dashboard shows the numbers. JWT auth with three roles, OpenAPI docs, a full test pyramid on a real PostgreSQL (Testcontainers), Docker image on every release, live demo. An Angular client ([`triagedesk-web`](https://github.com/gavinfecko/triagedesk-web)) consumes the API.

_Working name. Planning started 2026-09-24; Sprint 0 begins 2026-09-25._

## Docs
| Read this for | File |
|---|---|
| Why it exists, scope, release plan, risks, how to talk about it | [docs/PLAN.md](docs/PLAN.md) |
| System design: modules, domain model, state machine, SLA engine, security, API, data, tests, CI/CD | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| How the work is run: roles, cadence, board, stories, DoR/DoD, branching, releases, metrics, Jira mapping | [docs/PROCESS.md](docs/PROCESS.md) |
| Every user story with acceptance criteria and points (imported to Issues by a script) | [docs/BACKLOG.md](docs/BACKLOG.md) |
| The API contract, regenerated from the code on every build and diffed in CI | [docs/openapi.yaml](docs/openapi.yaml) |
| Decisions and their reasons | [docs/adr/](docs/adr/) |
| Sprint logs: goal, daily, review, retro, metrics | [docs/sprints/](docs/sprints/) |

## Run it (from Sprint 0 on)
```bash
# JDK 21 (Temurin from adoptium.net; Maven comes with the ./mvnw wrapper), Docker (Colima or Docker Desktop)
make up                          # PostgreSQL 16 + Mailpit in Docker (starts Colima if needed)
make run                         # dev profile: http://localhost:8080/swagger-ui.html
make verify                      # unit + slice + integration tests on real PostgreSQL, coverage gate, format check
```
`make` picks the JDK with `/usr/libexec/java_home -F -v 21` and points Testcontainers at the active Docker context, so Colima works without extra setup.

## How it is built
- **Modular monolith, package by feature** (`identity`, `ticket`, `sla`, `notification`, `reporting`), boundaries enforced by ArchUnit.
- **Ticket** is the aggregate; a tested transition table is the only way status changes; every change publishes a domain event that audit, SLA and notifications subscribe to.
- **SLA engine** is a pure `BusinessCalendar` (holidays, time zones, DST) with property-based tests, timers that pause in `PENDING`, and an idempotent breach scanner safe across instances.
- **Schema** is Flyway SQL, validated against the entities at startup; tests run on real PostgreSQL via Testcontainers, never H2.
- **Errors** are RFC 9457 Problem Details with a correlation id on every response and log line.

## Status
- [x] Concept, scope and release plan decided (2026-09-24, see docs/PLAN.md)
- [x] Architecture and ten ADRs written (2026-09-24, see docs/ARCHITECTURE.md, docs/adr/)
- [x] Agile process defined and backlog of 68 stories with acceptance criteria (2026-09-24, see docs/PROCESS.md, docs/BACKLOG.md)
- [ ] Sprint 0: skeleton, CI, board, error model, OpenAPI (`v0.1.0`)
- [ ] Sprint 1: identity and first ticket (`v0.2.0`)
- [ ] Sprint 2: agent workflow, state machine, audit (`v0.3.0`)
- [ ] Sprint 3: SLA engine and email (`v0.4.0`)
- [ ] Sprint 4: dashboard, hardening, Docker, live demo (`v1.0.0`)
- [ ] Sprints 5–6: Angular client (`triagedesk-web`)
- [ ] Published (public repo, pinned, portfolio page)

## License
MIT — see [LICENSE](LICENSE).
