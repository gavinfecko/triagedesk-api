# Sprint 0 — 2026-09-25 → 2026-09-28

## Goal
The skeleton runs against PostgreSQL with every quality gate on, CI is green on a pull request, the board is live with the whole backlog on it, and `GET /actuator/health` can be curled.

## Planning (2026-09-24, evening before)
- **Capacity:** four days, part-time (job search continues in parallel)
- **Velocity basis:** unknown (first sprint); committed 19 points as a deliberate over-reach for a setup sprint where most stories are mechanical. Anything not Done rolls into Sprint 1 ahead of TD-10.
- **Committed** (milestone `Sprint 0`):

| Story | Points | Notes |
|---|---|---|
| TD-9 Spike: Boot 4 starters, Security 7, springdoc (3 h) | 1 | First. Its findings go into ADR-0002 before TD-1 starts. |
| TD-1 Generate the Spring Boot skeleton | 2 | JDK 21 via `brew install openjdk@21`; Maven wrapper from Initializr |
| TD-2 Compose PostgreSQL + Mailpit, Flyway baseline | 2 | `make up` starts Colima |
| TD-4 Board, labels, milestones, templates, protection | 2 | `scripts/bootstrap-github.sh --apply` then `backlog_to_issues.py --apply` |
| TD-3 CI pipeline | 3 | First real PR on the repo; the branch protection makes it the first reviewed change |
| TD-5 Problem Details, validation, correlation IDs | 3 | First code with tests |
| TD-6 OpenAPI + Swagger + spec diff | 2 | |
| TD-8 ArchUnit + Spotless + JaCoCo gate | 2 | |
| TD-7 Actuator + JSON logs | 2 | |

- **Stretch:** none. Sprint 1 starts Monday regardless.

## Daily
| Day | Done since last entry | Next | Blockers |
|---|---|---|---|
| Thu 09-25 | | | |
| Fri 09-26 | | | |
| Sat 09-27 | | | |
| Sun 09-28 | | | |

## Review (Sunday 2026-09-28)
- **Demoed:**
- **Release:** `v0.1.0`
- **Not finished:**
- **Accepted by Product Owner:**

## Retro
| Keep | Stop | Try |
|---|---|---|
| | | |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 19 |
| Delivered points (velocity) | |
| Commitment accuracy | |
| Median cycle time | |
| Coverage | |
| Escaped bugs | |
